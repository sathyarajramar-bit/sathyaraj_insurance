package com.insurance.auth.security;

import com.insurance.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 401 for wrong email/password or an unusable refresh token. Message never reveals which part was wrong. */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", message);
    }
}
