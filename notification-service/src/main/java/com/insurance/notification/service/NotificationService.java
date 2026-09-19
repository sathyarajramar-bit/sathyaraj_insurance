package com.insurance.notification.service;

import com.insurance.common.dto.PageResponse;
import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.notification.NotificationRequest;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.common.security.CurrentUser;
import com.insurance.notification.client.RecipientResolver;
import com.insurance.notification.config.NotificationProperties;
import com.insurance.notification.dto.NotificationResponse;
import com.insurance.notification.entity.Channel;
import com.insurance.notification.entity.DeliveryStatus;
import com.insurance.notification.entity.Notification;
import com.insurance.notification.mapper.NotificationMapper;
import com.insurance.notification.provider.ChannelProvider;
import com.insurance.notification.repository.NotificationRepository;
import com.insurance.notification.template.NotificationTemplates;
import com.insurance.notification.template.RenderedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * accept(): store one row per channel (idempotent on key+channel), then try to deliver each right away.
 * Delivery failures leave the row FAILED for the retry job; the producer already got its 202.
 */
@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final RecipientResolver recipients;
    private final NotificationTemplates templates;
    private final Map<Channel, ChannelProvider> providers;
    private final NotificationProperties properties;
    private final NotificationMapper mapper;

    public NotificationService(NotificationRepository repository, RecipientResolver recipients, NotificationTemplates templates,
                               List<ChannelProvider> providerList, NotificationProperties properties, NotificationMapper mapper) {
        this.repository = repository;
        this.recipients = recipients;
        this.templates = templates;
        this.providers = providerList.stream().collect(Collectors.toMap(ChannelProvider::channel, Function.identity()));
        this.properties = properties;
        this.mapper = mapper;
    }

    public List<NotificationResponse> accept(NotificationRequest request) {
        RecipientResolver.Recipient recipient = recipients.resolve(request);
        Map<String, String> params = new java.util.HashMap<>(request.params() == null ? Map.of() : request.params());
        if (recipient.firstName() != null) {
            params.putIfAbsent("firstName", recipient.firstName());
        }
        RenderedMessage message = templates.render(request.eventType(), params);
        List<Notification> created = new ArrayList<>();
        if (recipient.email() != null) {
            store(request, Channel.EMAIL, recipient.email(), message.subject(), message.emailBody()).ifPresent(created::add);
        } else {
            log.warn("No e-mail recipient for {} {} (user {}, customer {})", request.eventType(), request.referenceNumber(), request.userId(), request.customerId());
        }
        if (properties.isSmsEnabled() && recipient.phone() != null && !recipient.phone().isBlank()) {
            store(request, Channel.SMS, recipient.phone(), null, message.smsBody()).ifPresent(created::add);
        }
        created.forEach(this::deliver);
        return created.stream().map(mapper::toResponse).toList();
    }

    private java.util.Optional<Notification> store(NotificationRequest request, Channel channel, String recipient, String subject, String body) {
        if (repository.existsByIdempotencyKeyAndChannel(request.idempotencyKey(), channel)) {
            log.info("Duplicate notification {} on {} ignored", request.idempotencyKey(), channel);
            return java.util.Optional.empty();
        }
        Instant now = Instant.now();
        try {
            return java.util.Optional.of(repository.saveAndFlush(Notification.builder()
                    .idempotencyKey(request.idempotencyKey()).eventType(request.eventType().name()).channel(channel)
                    .userId(request.userId()).customerId(request.customerId()).recipient(recipient)
                    .referenceType(request.referenceType()).referenceNumber(request.referenceNumber())
                    .subject(subject).body(body).status(DeliveryStatus.PENDING).attempts(0).createdAt(now).updatedAt(now)
                    .build()));
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate notification {} on {} ignored (race)", request.idempotencyKey(), channel);
            return java.util.Optional.empty();
        }
    }

    @Transactional
    public void deliver(Notification n) {
        ChannelProvider provider = providers.get(n.getChannel());
        n.setAttempts(n.getAttempts() + 1);
        n.setUpdatedAt(Instant.now());
        try {
            String messageId = provider.send(n.getRecipient(), n.getSubject(), n.getBody());
            n.setStatus(DeliveryStatus.SENT);
            n.setProvider(provider.name());
            n.setProviderMessageId(messageId);
            n.setSentAt(Instant.now());
            n.setLastError(null);
        } catch (Exception e) {
            n.setStatus(DeliveryStatus.FAILED);
            n.setLastError(e.toString().length() > 500 ? e.toString().substring(0, 500) : e.toString());
            log.warn("Delivery of {} {} to {} failed (attempt {}): {}", n.getEventType(), n.getReferenceNumber(), n.getRecipient(), n.getAttempts(), e.toString());
        }
        repository.save(n);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMine(Pageable pageable) {
        return PageResponse.from(repository.findByUserId(CurrentUser.require().userId(), pageable), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listByReference(String referenceType, String referenceNumber, Pageable pageable) {
        AuthenticatedUser user = CurrentUser.require();
        if (!user.hasAnyRole("ADMIN", "AGENT", "CLAIMS_HANDLER")) {
            throw new ForbiddenException("Only staff can list notifications by reference");
        }
        return PageResponse.from(repository.findByReferenceTypeAndReferenceNumber(referenceType, referenceNumber, pageable), mapper::toResponse);
    }

    public int retryFailed() {
        List<Notification> failed = repository.findTop100ByStatusAndAttemptsLessThanOrderByUpdatedAtAsc(DeliveryStatus.FAILED, properties.getRetry().getMaxAttempts());
        failed.forEach(this::deliver);
        return failed.size();
    }
}
