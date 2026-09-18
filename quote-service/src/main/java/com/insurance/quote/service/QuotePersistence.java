package com.insurance.quote.service;

import com.insurance.quote.entity.Quote;
import com.insurance.quote.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only transactional step of quote generation, kept in its own bean so the {@code @Transactional}
 * proxy is actually applied (a self-invocation inside QuoteService would bypass the proxy).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotePersistence {

    private final QuoteRepository quoteRepository;

    @Transactional
    public Quote save(Quote quote) {
        Quote saved = quoteRepository.save(quote);
        log.info("Generated quote {} for customer {} product {} final premium {}", saved.getQuoteNumber(),
                saved.getCustomerId(), saved.getProductCode(), saved.getFinalPremium());
        return saved;
    }
}
