package com.insurance.quote.repository;

import com.insurance.quote.entity.Quote;
import com.insurance.quote.entity.QuoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long>, JpaSpecificationExecutor<Quote> {

    Optional<Quote> findByQuoteNumber(String quoteNumber);

    /** Set-based expiry: one UPDATE for all overdue quotes instead of loading them one by one. */
    @Modifying(clearAutomatically = true)
    @Query("update Quote q set q.status = :expired, q.updatedAt = :now, q.updatedBy = 'quote-expiry-job' "
            + "where q.status = :generated and q.validUntil < :now")
    int expireOverdue(@Param("now") Instant now, @Param("generated") QuoteStatus generated, @Param("expired") QuoteStatus expired);
}
