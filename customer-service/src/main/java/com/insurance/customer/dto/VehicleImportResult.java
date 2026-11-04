package com.insurance.customer.dto;

import java.util.List;

/**
 * Outcome of {@code POST /api/customers/me/vehicles/import}. Always HTTP 200: row errors are a business
 * result the screen renders as a table, not a transport failure.
 *
 * <p>The file is all-or-nothing, like the retail fleet upload: nothing is saved while {@code errors} is
 * non-empty, so the customer fixes the sheet and uploads again.
 *
 * @param status        IMPORTED (rows saved), VALID (dry run, no errors, nothing saved), REJECTED (errors, nothing saved)
 * @param dryRun        echo of the request flag
 * @param totalRows     data rows found in the sheet (blank rows are ignored)
 * @param importedCount rows saved; 0 unless status is IMPORTED
 * @param errors        every problem found, ordered by row then column
 * @param vehicles      the saved vehicles when status is IMPORTED, otherwise empty
 */
public record VehicleImportResult(Status status, boolean dryRun, int totalRows, int importedCount,
                                  List<VehicleImportRowError> errors, List<VehicleResponse> vehicles) {

    public enum Status {
        IMPORTED, VALID, REJECTED
    }

    public static VehicleImportResult rejected(boolean dryRun, int totalRows, List<VehicleImportRowError> errors) {
        return new VehicleImportResult(Status.REJECTED, dryRun, totalRows, 0, errors, List.of());
    }

    public static VehicleImportResult valid(int totalRows) {
        return new VehicleImportResult(Status.VALID, true, totalRows, 0, List.of(), List.of());
    }

    public static VehicleImportResult imported(List<VehicleResponse> vehicles) {
        return new VehicleImportResult(Status.IMPORTED, false, vehicles.size(), vehicles.size(), List.of(), vehicles);
    }
}
