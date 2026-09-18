package com.insurance.quote.dto;

import java.math.BigDecimal;

public record QuoteAddOnResponse(String code, String name, BigDecimal premium) {
}
