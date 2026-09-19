package com.insurance.notification.repository;

import com.insurance.notification.entity.Channel;
import com.insurance.notification.entity.DeliveryStatus;
import com.insurance.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByIdempotencyKeyAndChannel(String idempotencyKey, Channel channel);

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Page<Notification> findByReferenceTypeAndReferenceNumber(String referenceType, String referenceNumber, Pageable pageable);

    List<Notification> findTop100ByStatusAndAttemptsLessThanOrderByUpdatedAtAsc(DeliveryStatus status, int maxAttempts);
}
