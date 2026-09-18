package com.insurance.common.dto;

/** One invalid request field, as reported by Bean Validation. */
public record FieldErrorDetail(String field, String message) {
}
