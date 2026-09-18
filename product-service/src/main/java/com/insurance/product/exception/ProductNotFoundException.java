package com.insurance.product.exception;

import com.insurance.common.exception.ResourceNotFoundException;

/** 404 with the platform error code RESOURCE_NOT_FOUND; a named subclass keeps call sites readable. */
public class ProductNotFoundException extends ResourceNotFoundException {

    public ProductNotFoundException(Long id) {
        super("Product", id);
    }
}
