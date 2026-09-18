package com.insurance.gateway.security;

/** Thrown when a JWT is missing required claims, is expired, or has a bad signature. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
