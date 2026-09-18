package com.insurance.customer.service;

import com.insurance.common.dto.PageResponse;
import com.insurance.common.exception.DuplicateResourceException;
import com.insurance.common.exception.ResourceNotFoundException;
import com.insurance.common.exception.ValidationException;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.common.security.CurrentUser;
import com.insurance.customer.config.CustomerProperties;
import com.insurance.customer.dto.CreateCustomerRequest;
import com.insurance.customer.dto.CustomerResponse;
import com.insurance.customer.dto.KycDecisionRequest;
import com.insurance.customer.dto.KycSubmissionRequest;
import com.insurance.customer.dto.UpdateCustomerRequest;
import com.insurance.customer.entity.Customer;
import com.insurance.customer.entity.KycStatus;
import com.insurance.customer.mapper.CustomerMapper;
import com.insurance.customer.repository.CustomerRepository;
import com.insurance.customer.repository.CustomerSpecifications;
import com.insurance.customer.security.CustomerAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper mapper;
    private final CustomerAccessPolicy accessPolicy;
    private final CustomerProperties properties;

    /**
     * Idempotent per userId: auth-service may retry after a timeout without creating duplicates.
     * Returns the existing profile in that case instead of failing.
     */
    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        return customerRepository.findByUserId(request.userId())
                .map(existing -> {
                    log.info("Profile for user {} already exists (id {}), returning it", request.userId(), existing.getId());
                    return mapper.toResponse(existing);
                })
                .orElseGet(() -> {
                    if (customerRepository.existsByEmailIgnoreCase(request.email())) {
                        throw new DuplicateResourceException("A customer with this email already exists");
                    }
                    Customer saved = customerRepository.save(Customer.builder()
                            .userId(request.userId())
                            .email(request.email().toLowerCase())
                            .firstName(request.firstName().trim())
                            .lastName(request.lastName().trim())
                            .phone(request.phone())
                            .build());
                    log.info("Created customer {} for user {}", saved.getId(), saved.getUserId());
                    return mapper.toResponse(saved);
                });
    }

    @Transactional(readOnly = true)
    public CustomerResponse getMine() {
        return mapper.toResponse(findMine());
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        Customer customer = find(id);
        accessPolicy.assertCanAccess(CurrentUser.require(), customer);
        return mapper.toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getByUserId(Long userId) {
        return mapper.toResponse(customerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for user " + userId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String name, String email, KycStatus kycStatus, Pageable pageable) {
        return PageResponse.from(
                customerRepository.findAll(CustomerSpecifications.withFilters(name, email, kycStatus), pageable),
                mapper::toResponse);
    }

    @Transactional
    public CustomerResponse updateMine(UpdateCustomerRequest request) {
        Customer customer = findMine();
        validateAge(request.dateOfBirth());
        mapper.updateFromRequest(request, customer);
        return mapper.toResponse(customer);
    }

    /** Customer uploads/changes a document: any previous decision is reset to PENDING. */
    @Transactional
    public CustomerResponse submitKyc(KycSubmissionRequest request) {
        Customer customer = findMine();
        customer.setKycDocumentType(request.documentType());
        customer.setKycDocumentNumber(request.documentNumber().trim());
        customer.setKycStatus(KycStatus.PENDING);
        customer.setKycVerifiedAt(null);
        log.info("Customer {} submitted KYC document {}", customer.getId(), request.documentType());
        return mapper.toResponse(customer);
    }

    @Transactional
    public CustomerResponse decideKyc(Long customerId, KycDecisionRequest request) {
        if (request.decision() == KycStatus.PENDING) {
            throw new ValidationException("Decision must be VERIFIED or REJECTED");
        }
        Customer customer = find(customerId);
        if (customer.getKycDocumentNumber() == null) {
            throw new ValidationException("Customer has not submitted a KYC document yet");
        }
        customer.setKycStatus(request.decision());
        customer.setKycVerifiedAt(request.decision() == KycStatus.VERIFIED ? Instant.now() : null);
        log.info("KYC of customer {} set to {} by user {}", customerId, request.decision(), CurrentUser.require().userId());
        return mapper.toResponse(customer);
    }

    Customer findMine() {
        AuthenticatedUser user = CurrentUser.require();
        return customerRepository.findByUserId(user.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for user " + user.userId()));
    }

    Customer find(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private void validateAge(LocalDate dateOfBirth) {
        if (dateOfBirth != null && Period.between(dateOfBirth, LocalDate.now()).getYears() < properties.getMinimumAgeYears()) {
            throw new ValidationException("Customer must be at least " + properties.getMinimumAgeYears() + " years old");
        }
    }
}
