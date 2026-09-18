package com.insurance.quote.controller;

import com.insurance.common.dto.PageResponse;
import com.insurance.quote.dto.QuoteRequest;
import com.insurance.quote.dto.QuoteResponse;
import com.insurance.quote.entity.QuoteStatus;
import com.insurance.quote.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Quotes", description = "Premium calculation and quote lifecycle")
public class QuoteController {

    private final QuoteService quoteService;

    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('CUSTOMER','AGENT','ADMIN')")
    @Operation(summary = "Price a selection without saving it (status DRAFT)")
    public QuoteResponse preview(@Valid @RequestBody QuoteRequest request) {
        return quoteService.preview(request);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','AGENT','ADMIN')")
    @Operation(summary = "Generate a quote (status GENERATED, valid for the configured period)")
    public ResponseEntity<QuoteResponse> generate(@Valid @RequestBody QuoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quoteService.generate(request));
    }

    @GetMapping("/{quoteNumber}")
    @Operation(summary = "Quote by number: owner, ADMIN, AGENT or SERVICE")
    public QuoteResponse get(@PathVariable String quoteNumber) {
        return quoteService.getByNumber(quoteNumber);
    }

    @GetMapping
    @Operation(summary = "My quotes (customers) or all quotes with filters (ADMIN/AGENT): ?customerId=&status=&productId=&page=&size=&sort=")
    public PageResponse<QuoteResponse> search(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) QuoteStatus status,
            @RequestParam(required = false) Long productId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return quoteService.search(customerId, status, productId, pageable);
    }

    @PostMapping("/{quoteNumber}/accept")
    @PreAuthorize("hasAnyRole('CUSTOMER','AGENT','ADMIN')")
    @Operation(summary = "Accept a GENERATED quote (required before creating a proposal)")
    public QuoteResponse accept(@PathVariable String quoteNumber) {
        return quoteService.accept(quoteNumber);
    }

    @PostMapping("/{quoteNumber}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER','AGENT','ADMIN')")
    @Operation(summary = "Cancel a quote")
    public QuoteResponse cancel(@PathVariable String quoteNumber) {
        return quoteService.cancel(quoteNumber);
    }
}
