package com.insurance.document.entity;

/** Stored as VARCHAR(30): adding a constant needs no migration, renaming one does. */
public enum DocumentType {
    POLICY_SCHEDULE, CLAIM_PHOTO, FIR, REPAIR_ESTIMATE, ID_PROOF, ADDRESS_PROOF,
    /** Driving licence card (front). Has a dedicated field extractor. */
    DRIVING_LICENSE,
    /** Vehicle registration card (Mulkiya). Generic extraction until a dedicated extractor lands. */
    VEHICLE_REGISTRATION,
    EMIRATES_ID,
    OTHER
}
