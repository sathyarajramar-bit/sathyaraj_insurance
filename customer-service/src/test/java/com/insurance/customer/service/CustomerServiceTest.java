package com.insurance.customer.service;

import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.exception.ValidationException;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.customer.config.CustomerProperties;
import com.insurance.customer.dto.CreateCustomerRequest;
import com.insurance.customer.dto.CustomerResponse;
import com.insurance.customer.dto.KycDecisionRequest;
import com.insurance.customer.dto.UpdateCustomerRequest;
import com.insurance.customer.entity.Customer;
import com.insurance.customer.entity.KycStatus;
import com.insurance.customer.mapper.CustomerMapper;
import com.insurance.customer.repository.CustomerRepository;
import com.insurance.customer.security.CustomerAccessPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;

    private CustomerService service() {
        return new CustomerService(customerRepository, Mappers.getMapper(CustomerMapper.class),
                new CustomerAccessPolicy(), new CustomerProperties());
    }

    private static Customer customer(long id, long userId) {
        Customer c = Customer.builder().userId(userId).email("u" + userId + "@x.com").firstName("F").lastName("L").build();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static void actAs(long userId, String... roles) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "u@x.com", List.of(roles));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createIsIdempotentPerUserId() {
        when(customerRepository.findByUserId(7L)).thenReturn(Optional.of(customer(3L, 7L)));

        CustomerResponse response = service().create(new CreateCustomerRequest(7L, "u7@x.com", "F", "L", null));

        assertThat(response.id()).isEqualTo(3L);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void customerCannotReadAnotherCustomersProfile() {
        when(customerRepository.findById(3L)).thenReturn(Optional.of(customer(3L, 7L)));
        actAs(99L, "CUSTOMER");

        assertThatThrownBy(() -> service().getById(3L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void agentCanReadAnyProfile() {
        when(customerRepository.findById(3L)).thenReturn(Optional.of(customer(3L, 7L)));
        actAs(99L, "AGENT");

        assertThat(service().getById(3L).userId()).isEqualTo(7L);
    }

    @Test
    void underageDateOfBirthIsRejected() {
        when(customerRepository.findByUserId(7L)).thenReturn(Optional.of(customer(3L, 7L)));
        actAs(7L, "CUSTOMER");

        UpdateCustomerRequest request = new UpdateCustomerRequest("F", "L", null, LocalDate.now().minusYears(17), null, null);
        assertThatThrownBy(() -> service().updateMine(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least 18");
    }

    @Test
    void kycCannotBeDecidedWithoutASubmittedDocument() {
        when(customerRepository.findById(3L)).thenReturn(Optional.of(customer(3L, 7L)));
        actAs(1L, "ADMIN");

        assertThatThrownBy(() -> service().decideKyc(3L, new KycDecisionRequest(KycStatus.VERIFIED)))
                .isInstanceOf(ValidationException.class);
    }
}
