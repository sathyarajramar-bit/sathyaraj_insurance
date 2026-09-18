package com.insurance.product.cache;

import com.insurance.product.dto.ProductResponse;
import com.insurance.product.entity.ProductType;
import com.insurance.product.entity.VehicleType;
import com.insurance.product.repository.ProductRepository;
import com.insurance.product.service.ProductService;
import com.insurance.product.service.ProductServiceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies the caching contract (hit, key, eviction) through the Spring proxy with the in-memory
 * cache. Redis specific serialisation is covered by ProductRedisCacheIT when Docker is available.
 */
@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
class ProductCachingIT {

    private static final String CATALOG_KEY = "type=MOTOR|vt=null|ct=null|active=true|page=0|size=5|sort=UNSORTED";

    @Autowired private ProductService productService;
    @Autowired private CacheManager cacheManager;
    @MockitoSpyBean private ProductRepository repository;

    @Test
    void productDetailsAreServedFromCacheUntilTheProductChanges() {
        ProductResponse created = productService.create(ProductServiceTest.request("CACHE-TEST-1", ProductType.MOTOR,
                VehicleType.CAR, List.of(ProductServiceTest.coverage("OWN_DAMAGE")), null, null));
        clearInvocations(repository);

        productService.getById(created.id());
        productService.getById(created.id());
        verify(repository, times(1)).findById(created.id());
        assertThat(cacheManager.getCache("products").get(created.id())).isNotNull();

        productService.setActive(created.id(), false);
        assertThat(cacheManager.getCache("products").get(created.id())).isNull();

        productService.getById(created.id());
        verify(repository, times(3)).findById(created.id());
    }

    @Test
    void catalogPagesAreCachedPerFilterAndEvictedOnAnyWrite() {
        productService.search(ProductType.MOTOR, null, null, true, PageRequest.of(0, 5));
        productService.search(ProductType.MOTOR, null, null, true, PageRequest.of(0, 5));
        assertThat(cacheManager.getCache("product-catalog").get(CATALOG_KEY)).isNotNull();

        productService.create(ProductServiceTest.request("CACHE-TEST-2", ProductType.MOTOR, VehicleType.BIKE,
                List.of(ProductServiceTest.coverage("TP")), null, null));

        assertThat(cacheManager.getCache("product-catalog").get(CATALOG_KEY)).isNull();
    }
}
