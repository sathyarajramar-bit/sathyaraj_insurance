package com.insurance.quote.service;

import com.insurance.common.dto.PageResponse;
import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.exception.QuoteExpiredException;
import com.insurance.common.exception.ValidationException;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.common.security.CurrentUser;
import com.insurance.quote.client.CustomerClient;
import com.insurance.quote.client.CustomerGateway;
import com.insurance.quote.client.ProductCatalogGateway;
import com.insurance.quote.client.ProductClient;
import com.insurance.quote.config.CacheNames;
import com.insurance.quote.config.QuoteProperties;
import com.insurance.quote.dto.QuoteRequest;
import com.insurance.quote.dto.QuoteResponse;
import com.insurance.quote.entity.Quote;
import com.insurance.quote.entity.QuoteAddOn;
import com.insurance.quote.entity.QuoteStatus;
import com.insurance.quote.event.QuoteEventPublisher;
import com.insurance.quote.exception.QuoteIneligibleException;
import com.insurance.quote.exception.QuoteNotFoundException;
import com.insurance.quote.exception.QuoteStateException;
import com.insurance.quote.mapper.QuoteMapper;
import com.insurance.quote.pricing.PremiumCalculator;
import com.insurance.quote.pricing.PremiumResult;
import com.insurance.quote.pricing.PricingInput;
import com.insurance.quote.repository.QuoteRepository;
import com.insurance.quote.repository.QuoteSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quote use cases. The remote reads (customer, vehicle, product, pricing, eligibility) happen BEFORE the
 * transaction starts: holding a database transaction open across network calls would pin a connection
 * for the whole round trip. Only the insert is transactional.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final CustomerGateway customerGateway;
    private final ProductCatalogGateway productGateway;
    private final PremiumCalculator premiumCalculator;
    private final QuoteNumberGenerator numberGenerator;
    private final QuoteAccessPolicy accessPolicy;
    private final QuoteMapper mapper;
    private final QuoteProperties properties;
    private final QuoteEventPublisher events;
    private final QuotePersistence persistence;
    private final QuoteReadCache readCache;

    /** Prices without persisting (status DRAFT): lets the customer play with add-ons before generating. */
    public QuoteResponse preview(QuoteRequest request) {
        Quote quote = build(request, CurrentUser.require());
        quote.setStatus(QuoteStatus.DRAFT);
        return mapper.toResponse(quote);
    }

    public QuoteResponse generate(QuoteRequest request) {
        Quote quote = build(request, CurrentUser.require());
        QuoteResponse response = mapper.toResponse(persistence.save(quote));
        events.quoteGenerated(response);
        return response;
    }

    /** Cached load (see QuoteReadCache) followed by the ownership check on every call. */
    public QuoteResponse getByNumber(String quoteNumber) {
        QuoteResponse quote = readCache.load(quoteNumber);
        accessPolicy.assertCanRead(CurrentUser.require(), quote.userId());
        return quote;
    }

    @Transactional(readOnly = true)
    public PageResponse<QuoteResponse> search(Long customerId, QuoteStatus status, Long productId, Pageable pageable) {
        AuthenticatedUser user = CurrentUser.require();
        Long userFilter = user.hasAnyRole("ADMIN", "AGENT") ? null : user.userId();
        if (userFilter != null && customerId != null) {
            throw new ForbiddenException("Customers can only list their own quotes");
        }
        return PageResponse.from(quoteRepository.findAll(
                QuoteSpecifications.withFilters(userFilter, customerId, status, productId), pageable), mapper::toResponse);
    }

    @CacheEvict(cacheNames = CacheNames.QUOTES, key = "#quoteNumber")
    @Transactional
    public QuoteResponse accept(String quoteNumber) {
        Quote quote = find(quoteNumber);
        accessPolicy.assertCanModify(CurrentUser.require(), quote);
        Instant now = Instant.now();
        if (quote.isExpired(now)) {
            throw new QuoteExpiredException(quoteNumber);
        }
        if (quote.getStatus() != QuoteStatus.GENERATED) {
            throw new QuoteStateException("Quote " + quoteNumber + " cannot be accepted from status " + quote.getStatus());
        }
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setAcceptedAt(now);
        log.info("Quote {} accepted", quoteNumber);
        return mapper.toResponse(quote);
    }

    @CacheEvict(cacheNames = CacheNames.QUOTES, key = "#quoteNumber")
    @Transactional
    public QuoteResponse cancel(String quoteNumber) {
        Quote quote = find(quoteNumber);
        accessPolicy.assertCanModify(CurrentUser.require(), quote);
        if (quote.getStatus() == QuoteStatus.CANCELLED || quote.getStatus() == QuoteStatus.EXPIRED) {
            throw new QuoteStateException("Quote " + quoteNumber + " is already " + quote.getStatus());
        }
        quote.setStatus(QuoteStatus.CANCELLED);
        quote.setCancelledAt(Instant.now());
        return mapper.toResponse(quote);
    }

    Quote find(String quoteNumber) {
        return quoteRepository.findByQuoteNumber(quoteNumber).orElseThrow(() -> new QuoteNotFoundException(quoteNumber));
    }

    private Quote build(QuoteRequest request, AuthenticatedUser user) {
        CustomerClient.CustomerProfile customer = resolveCustomer(request, user);
        CustomerClient.Vehicle vehicle = customerGateway.getVehicle(customer.id(), request.vehicleId());
        ProductClient.ProductDetails product = productGateway.getProduct(request.productId());
        if (!product.active()) {
            throw new ValidationException("Product " + product.code() + " is not available for sale");
        }
        int driverAge = resolveDriverAge(request, customer);
        int vehicleAge = Math.max(0, Year.now().getValue() - vehicle.manufacturingYear());
        BigDecimal ncb = request.ncbPercent() == null ? BigDecimal.ZERO : request.ncbPercent();

        ProductClient.EligibilityResult eligibility = productGateway.checkEligibility(product.id(),
                new ProductClient.RiskProfile(vehicle.vehicleType(), vehicleAge, vehicle.currentValue(),
                        vehicle.engineCapacityCc(), vehicle.fuelType(), driverAge));
        if (!eligibility.eligible()) {
            throw new QuoteIneligibleException(product.code(), eligibility.violations());
        }
        List<PricingInput.AddOnInput> addOns = resolveAddOns(request, product);
        ProductClient.ProductPricing rates = productGateway.getPricing(product.id());

        PremiumResult premium = premiumCalculator.calculate(new PricingInput(product.coverageType(), vehicle.currentValue(),
                vehicleAge, vehicle.fuelType(), driverAge, ncb, addOns,
                new PricingInput.Rates(rates.baseRatePercentOfIdv(), rates.minBasePremium(), rates.thirdPartyPremium(),
                        rates.vehicleAgeLoadingPercentPerYear(), rates.maxVehicleAgeLoadingPercent(), rates.youngDriverAgeLimit(),
                        rates.youngDriverLoadingPercent(), rates.maxNcbDiscountPercent(), rates.electricVehicleDiscountPercent(),
                        rates.taxPercent())));

        Quote quote = Quote.builder()
                .quoteNumber(numberGenerator.next()).status(QuoteStatus.GENERATED)
                .userId(customer.userId()).customerId(customer.id())
                .productId(product.id()).productCode(product.code()).productName(product.name())
                .coverageType(product.coverageType()).termMonths(product.termMonths())
                .vehicleId(vehicle.id()).registrationNumber(vehicle.registrationNumber()).vehicleType(vehicle.vehicleType())
                .make(vehicle.make()).model(vehicle.model()).fuelType(vehicle.fuelType())
                .manufacturingYear(vehicle.manufacturingYear()).engineCapacityCc(vehicle.engineCapacityCc())
                .idv(vehicle.currentValue()).driverAge(driverAge).ncbPercent(ncb)
                .ownDamagePremium(premium.ownDamagePremium()).thirdPartyPremium(premium.thirdPartyPremium())
                .basePremium(premium.basePremium()).addOnPremium(premium.addOnPremium())
                .discountAmount(premium.discountAmount()).taxAmount(premium.taxAmount()).finalPremium(premium.finalPremium())
                .validUntil(Instant.now().plus(properties.getValidity()))
                .build();
        premium.addOns().forEach(a -> quote.addAddOn(QuoteAddOn.builder().code(a.code()).name(a.name()).premium(a.premium()).build()));
        return quote;
    }

    private CustomerClient.CustomerProfile resolveCustomer(QuoteRequest request, AuthenticatedUser user) {
        if (request.customerId() != null) {
            if (!user.hasAnyRole("ADMIN", "AGENT")) {
                throw new ForbiddenException("Only agents can quote on behalf of a customer");
            }
            return customerGateway.getById(request.customerId());
        }
        return customerGateway.getByUserId(user.userId());
    }

    private static int resolveDriverAge(QuoteRequest request, CustomerClient.CustomerProfile customer) {
        if (request.driverAge() != null) {
            return request.driverAge();
        }
        if (customer.dateOfBirth() == null) {
            throw new ValidationException("Driver age is required (no date of birth on the customer profile)");
        }
        return Period.between(customer.dateOfBirth(), LocalDate.now()).getYears();
    }

    /** Selected add-ons must exist on the product and be active; codes are matched case-insensitively. */
    private static List<PricingInput.AddOnInput> resolveAddOns(QuoteRequest request, ProductClient.ProductDetails product) {
        if (request.addOnCodes() == null || request.addOnCodes().isEmpty()) {
            return List.of();
        }
        Map<String, ProductClient.ProductAddOn> available = product.addOns().stream()
                .filter(ProductClient.ProductAddOn::active)
                .collect(Collectors.toMap(a -> a.code().toUpperCase(), Function.identity()));
        Set<String> seen = new HashSet<>();
        return request.addOnCodes().stream()
                .map(String::toUpperCase)
                .filter(seen::add)
                .map(code -> {
                    ProductClient.ProductAddOn addOn = available.get(code);
                    if (addOn == null) {
                        throw new ValidationException("Add-on " + code + " is not available for product " + product.code());
                    }
                    return new PricingInput.AddOnInput(addOn.code(), addOn.name(), addOn.pricingType(), addOn.rate());
                })
                .toList();
    }
}
