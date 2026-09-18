package com.insurance.quote.scheduler;

import com.insurance.quote.config.CacheNames;
import com.insurance.quote.entity.QuoteStatus;
import com.insurance.quote.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Moves GENERATED quotes past their validity to EXPIRED with one set-based UPDATE.
 *
 * <p>Interview notes: the read path already treats an overdue GENERATED quote as expired (accept fails),
 * so the job is about data hygiene and reporting, not correctness. With several instances the job runs
 * on each of them; the UPDATE is idempotent so that is harmless, and ShedLock would make it run once.
 * The cron comes from configuration; the Spring value "-" disables the job (used in tests).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteExpiryScheduler {

    private final QuoteRepository quoteRepository;
    private final CacheManager cacheManager;

    @Scheduled(cron = "${quote.expiry.cron:0 */10 * * * *}")
    @Transactional
    public int expireOverdueQuotes() {
        int expired = quoteRepository.expireOverdue(Instant.now(), QuoteStatus.GENERATED, QuoteStatus.EXPIRED);
        if (expired > 0) {
            log.info("Expired {} quote(s)", expired);
            Cache cache = cacheManager.getCache(CacheNames.QUOTES);
            if (cache != null) {
                cache.clear();   // bulk update bypasses per-key eviction; the cache is small and short-lived
            }
        }
        return expired;
    }
}
