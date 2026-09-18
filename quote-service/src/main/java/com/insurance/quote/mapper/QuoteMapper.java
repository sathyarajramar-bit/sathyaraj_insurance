package com.insurance.quote.mapper;

import com.insurance.quote.dto.QuoteAddOnResponse;
import com.insurance.quote.dto.QuoteResponse;
import com.insurance.quote.entity.Quote;
import com.insurance.quote.entity.QuoteAddOn;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface QuoteMapper {

    QuoteResponse toResponse(Quote quote);

    QuoteAddOnResponse toResponse(QuoteAddOn addOn);
}
