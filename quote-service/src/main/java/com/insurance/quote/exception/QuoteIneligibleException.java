package com.insurance.quote.exception;

import com.insurance.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.List;

/** 422: the product's eligibility rules reject this vehicle/driver; the message lists every reason. */
public class QuoteIneligibleException extends BusinessException {

    public QuoteIneligibleException(String productCode, List<String> violations) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "QUOTE_INELIGIBLE",
                "Not eligible for " + productCode + ": " + String.join("; ", violations));
    }
}
