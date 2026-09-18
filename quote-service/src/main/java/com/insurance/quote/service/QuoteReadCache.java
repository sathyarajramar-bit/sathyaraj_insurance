package com.insurance.quote.service;

import com.insurance.quote.config.CacheNames;
import com.insurance.quote.dto.QuoteResponse;
import com.insurance.quote.exception.QuoteNotFoundException;
import com.insurance.quote.mapper.QuoteMapper;
import com.insurance.quote.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cached load of a quote by number, deliberately WITHOUT any authorization: a cached method must not
 * contain per-caller logic, otherwise the first caller's access decision is replayed for everyone.
 * {@link QuoteService#getByNumber} performs the ownership check on the returned DTO on every call.
 */
@Component
@RequiredArgsConstructor
public class QuoteReadCache {

    private final QuoteRepository quoteRepository;
    private final QuoteMapper mapper;

    @Cacheable(cacheNames = CacheNames.QUOTES, key = "#quoteNumber")
    @Transactional(readOnly = true)
    public QuoteResponse load(String quoteNumber) {
        return quoteRepository.findByQuoteNumber(quoteNumber).map(mapper::toResponse)
                .orElseThrow(() -> new QuoteNotFoundException(quoteNumber));
    }
}
