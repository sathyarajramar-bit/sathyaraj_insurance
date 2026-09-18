package com.insurance.product.service;

import com.insurance.common.dto.PageResponse;
import com.insurance.common.exception.DuplicateResourceException;
import com.insurance.common.exception.ValidationException;
import com.insurance.product.config.CacheNames;
import com.insurance.product.dto.PricingResponse;
import com.insurance.product.dto.ProductRequest;
import com.insurance.product.dto.ProductResponse;
import com.insurance.product.dto.ProductSummaryResponse;
import com.insurance.product.dto.AddOnRequest;
import com.insurance.product.dto.CoverageRequest;
import com.insurance.product.entity.AddOn;
import com.insurance.product.entity.Coverage;
import com.insurance.product.entity.CoverageType;
import com.insurance.product.entity.EligibilityRule;
import com.insurance.product.entity.Product;
import com.insurance.product.entity.ProductType;
import com.insurance.product.entity.VehicleType;
import com.insurance.product.exception.ProductNotFoundException;
import com.insurance.product.mapper.ProductMapper;
import com.insurance.product.repository.ProductRepository;
import com.insurance.product.repository.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Catalogue use cases. Reads are cached (see {@link CacheNames}); every write evicts exactly the
 * entries it can affect, so readers never see a stale product for longer than one request.
 * The cached value is always the DTO, never the entity (entities carry lazy proxies; DTOs are plain JSON).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper mapper;
    private final ProductRequestValidator validator;

    @Cacheable(cacheNames = CacheNames.PRODUCT_CATALOG,
            key = "'type=' + #type + '|vt=' + #vehicleType + '|ct=' + #coverageType + '|active=' + #active"
                    + " + '|page=' + #pageable.pageNumber + '|size=' + #pageable.pageSize + '|sort=' + #pageable.sort")
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(ProductType type, VehicleType vehicleType, CoverageType coverageType,
                                                       Boolean active, Pageable pageable) {
        return PageResponse.from(
                productRepository.findAll(ProductSpecifications.withFilters(type, vehicleType, coverageType, active), pageable),
                mapper::toSummary);
    }

    @Cacheable(cacheNames = CacheNames.PRODUCTS, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return mapper.toResponse(find(id));
    }

    @Cacheable(cacheNames = CacheNames.PRODUCT_PRICING, key = "#id")
    @Transactional(readOnly = true)
    public PricingResponse getPricing(Long id) {
        return mapper.toResponse(find(id).getPricing());
    }

    @CacheEvict(cacheNames = CacheNames.PRODUCT_CATALOG, allEntries = true)
    @Transactional
    public ProductResponse create(ProductRequest request) {
        validator.validate(request);
        if (productRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Product code " + request.code() + " already exists");
        }
        Product product = mapper.toEntity(request);
        applyChildren(product, request);
        Product saved = productRepository.save(product);
        log.info("Created product {} ({})", saved.getId(), saved.getCode());
        return mapper.toResponse(saved);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_PRICING, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_CATALOG, allEntries = true)})
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        validator.validate(request);
        Product product = find(id);
        if (!product.getCode().equals(request.code())) {
            throw new ValidationException("Product code cannot be changed (policies reference it)");
        }
        mapper.updateEntity(request, product);
        applyChildren(product, request);
        log.info("Updated product {} ({})", id, product.getCode());
        return mapper.toResponse(product);
    }

    /**
     * Products are never hard-deleted: quotes and policies reference them by id. DELETE deactivates,
     * which removes the product from the catalogue and blocks new quotes while history stays intact.
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_CATALOG, allEntries = true)})
    @Transactional
    public ProductResponse setActive(Long id, boolean active) {
        Product product = find(id);
        product.setActive(active);
        log.info("Product {} ({}) {}", id, product.getCode(), active ? "activated" : "deactivated");
        return mapper.toResponse(product);
    }

    Product find(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Merges the request into the aggregate: children present in both are updated in place (ids stay
     * stable), missing ones are removed (orphanRemoval issues the DELETE), new ones are inserted.
     * A naive clear-and-add would make Hibernate INSERT the new row before DELETING the old one and
     * trip the (product_id, code) unique key.
     */
    private void applyChildren(Product product, ProductRequest request) {
        sync(product.getCoverages(), request.coverages(), Coverage::getCode, CoverageRequest::code,
                r -> attach(mapper.toEntity(r), product), mapper::updateEntity);
        sync(product.getAddOns(), request.addOns() == null ? List.of() : request.addOns(), AddOn::getCode, AddOnRequest::code,
                r -> attach(mapper.toEntity(r), product), mapper::updateEntity);
        sync(product.getEligibilityRules(), request.eligibilityRules() == null ? List.of() : request.eligibilityRules(),
                r -> r.getRuleType().name(), r -> r.ruleType().name(),
                r -> attach(mapper.toEntity(r), product), mapper::updateEntity);
        if (product.getPricing() == null) {
            product.attachPricing(mapper.toEntity(request.pricing()));
        } else {
            mapper.updateEntity(request.pricing(), product.getPricing());
        }
    }

    private static <E, R> void sync(List<E> current, List<R> requests, Function<E, String> currentKey,
                                    Function<R, String> requestKey, Function<R, E> create, BiConsumer<R, E> update) {
        Map<String, E> byKey = new HashMap<>();
        current.forEach(e -> byKey.put(currentKey.apply(e), e));
        Set<String> wanted = new HashSet<>();
        requests.forEach(r -> wanted.add(requestKey.apply(r)));
        current.removeIf(e -> !wanted.contains(currentKey.apply(e)));
        for (R request : requests) {
            E existing = byKey.get(requestKey.apply(request));
            if (existing != null) {
                update.accept(request, existing);
            } else {
                current.add(create.apply(request));
            }
        }
    }

    private static Coverage attach(Coverage coverage, Product product) {
        coverage.setProduct(product);
        return coverage;
    }

    private static AddOn attach(AddOn addOn, Product product) {
        addOn.setProduct(product);
        return addOn;
    }

    private static EligibilityRule attach(EligibilityRule rule, Product product) {
        rule.setProduct(product);
        return rule;
    }
}
