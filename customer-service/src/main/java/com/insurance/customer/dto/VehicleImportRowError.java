package com.insurance.customer.dto;

/**
 * One problem found in one spreadsheet row.
 *
 * @param row     1-based row number as the user sees it in the spreadsheet (the header is row 1)
 * @param column  header of the offending column, or {@code null} for a row-level problem (duplicate, too old)
 * @param message what is wrong, in the same wording as the single-vehicle form
 */
public record VehicleImportRowError(int row, String column, String message) {
}
