package com.insurance.proposal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Beneficiary of the personal accident cover; mandatory before submission. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Nominee {

    @Column(name = "nominee_name", length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "nominee_relationship", length = 20)
    private NomineeRelationship relationship;

    @Column(name = "nominee_dob")
    private LocalDate dateOfBirth;

    public boolean isComplete() {
        return name != null && !name.isBlank() && relationship != null && dateOfBirth != null;
    }
}
