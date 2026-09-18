package com.insurance.quote.service;

import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.exception.QuoteExpiredException;
import com.insurance.common.exception.ValidationException;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.quote.client.CustomerClient;
import com.insurance.quote.client.CustomerGateway;
import com.insurance.quote.client.ProductCatalogGateway;
import com.insurance.quote.client.ProductClient;
import com.insurance.quote.config.QuoteProperties;
import com.insurance.quote.dto.QuoteRequest;
import com.insurance.quote.entity.Quote;
import com.insurance.quote.entity.QuoteStatus;
import com.insurance.quote.event.QuoteEventPublisher;
import com.insurance.quote.exception.QuoteIneligibleException;
import com.insurance.quote.mapper.QuoteMapper;
import com.insurance.quote.pricing.PremiumCalculator;
import com.insurance.quote.repository.QuoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock private QuoteRepository quoteRepository;
    @Mock private CustomerGateway customerGateway;
    @Mock private ProductCatalogGateway productGateway;
    @Mock private QuoteEventPublisher events;
    @Mock private QuotePersistence persistence;

    private QuoteService service;

    @BeforeEach
    void setUp() {
        service = new QuoteService(quoteRepository, customerGateway, productGateway, new PremiumCalculator(),
                new QuoteNumberGenerator(), new QuoteAccessPolicy(), Mappers.getMapper(QuoteMapper.class),
                new QuoteProperties(), events, persistence, new QuoteReadCache(quoteRepository, Mappers.getMapper(QuoteMapper.class)));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    static void actAs(long userId, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "u@x.com", List.of(roles)), null, List.of()));
    }

    static ProductClient.ProductDetails carProduct() {
        return new ProductClient.ProductDetails(1L, "MOTOR-CAR-COMP", "Car", "MOTOR", "CAR", "COMPREHENSIVE", 12, true,
                List.of(new ProductClient.ProductAddOn(10L, "ZERO_DEPRECIATION", "Zero dep", "PERCENT_OF_IDV", new BigDecimal("0.4"), true),
                        new ProductClient.ProductAddOn(11L, "OLD_ADDON", "Retired", "FLAT", BigDecimal.TEN, false)));
    }

    static CustomerClient.Vehicle car() {
        return new CustomerClient.Vehicle(5L, 7L, "MH12AB1234", "CAR", "Honda", "City", "PETROL", 2023, 1498, new BigDecimal("600000"));
    }

    private void stubHappyPath() {
        when(customerGateway.getByUserId(42L)).thenReturn(new CustomerClient.CustomerProfile(7L, 42L, "u@x.com", "J", "D", LocalDate.of(1990, 1, 1), "PENDING"));
        when(customerGateway.getVehicle(7L, 5L)).thenReturn(car());
        when(productGateway.getProduct(1L)).thenReturn(carProduct());
    }

    @Test
    void ineligibleVehicleIsRejectedBeforePricing() {
        actAs(42L, "CUSTOMER");
        stubHappyPath();
        when(productGateway.checkEligibility(anyLong(), any())).thenReturn(new ProductClient.EligibilityResult(1L, "MOTOR-CAR-COMP", false, List.of("too old")));

        assertThatThrownBy(() -> service.generate(new QuoteRequest(1L, 5L, null, null, null, null)))
                .isInstanceOf(QuoteIneligibleException.class)
                .hasMessageContaining("too old");
        verify(productGateway, never()).getPricing(anyLong());
        verify(persistence, never()).save(any());
    }

    @Test
    void unknownOrInactiveAddOnIsRejected() {
        actAs(42L, "CUSTOMER");
        stubHappyPath();
        when(productGateway.checkEligibility(anyLong(), any())).thenReturn(new ProductClient.EligibilityResult(1L, "MOTOR-CAR-COMP", true, List.of()));

        assertThatThrownBy(() -> service.preview(new QuoteRequest(1L, 5L, List.of("OLD_ADDON"), null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("OLD_ADDON is not available");
    }

    @Test
    void customerCannotQuoteOnBehalfOfAnotherCustomer() {
        actAs(42L, "CUSTOMER");
        assertThatThrownBy(() -> service.generate(new QuoteRequest(1L, 5L, null, null, null, 99L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void driverAgeIsRequiredWhenProfileHasNoDateOfBirth() {
        actAs(42L, "CUSTOMER");
        when(customerGateway.getByUserId(42L)).thenReturn(new CustomerClient.CustomerProfile(7L, 42L, "u@x.com", "J", "D", null, "PENDING"));
        when(customerGateway.getVehicle(7L, 5L)).thenReturn(car());
        when(productGateway.getProduct(1L)).thenReturn(carProduct());

        assertThatThrownBy(() -> service.preview(new QuoteRequest(1L, 5L, null, null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Driver age is required");
    }

    @Test
    void expiredQuoteCannotBeAccepted() {
        actAs(42L, "CUSTOMER");
        Quote quote = Quote.builder().quoteNumber("QT-1").status(QuoteStatus.GENERATED).userId(42L)
                .validUntil(Instant.now().minusSeconds(1)).build();
        when(quoteRepository.findByQuoteNumber("QT-1")).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.accept("QT-1")).isInstanceOf(QuoteExpiredException.class);
    }

    @Test
    void otherCustomerCannotAcceptQuote() {
        actAs(99L, "CUSTOMER");
        Quote quote = Quote.builder().quoteNumber("QT-1").status(QuoteStatus.GENERATED).userId(42L)
                .validUntil(Instant.now().plusSeconds(600)).build();
        when(quoteRepository.findByQuoteNumber("QT-1")).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.accept("QT-1")).isInstanceOf(ForbiddenException.class);
    }
}
