package com.insurance.document.exception;

import com.insurance.common.exception.ResourceNotFoundException;

public class DocumentNotFoundException extends ResourceNotFoundException {

    public DocumentNotFoundException(Long id) {
        super("Document", id);
    }
}
