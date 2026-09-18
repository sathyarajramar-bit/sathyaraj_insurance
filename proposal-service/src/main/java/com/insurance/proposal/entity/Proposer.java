package com.insurance.proposal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** The person applying (prefilled from the customer profile, editable while DRAFT). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Proposer {

    @Column(name = "proposer_first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "proposer_last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "proposer_email", nullable = false)
    private String email;

    @Column(name = "proposer_phone", length = 20)
    private String phone;

    @Column(name = "proposer_dob")
    private LocalDate dateOfBirth;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 100)
    private String country;

    public boolean isComplete() {
        return notBlank(firstName) && notBlank(lastName) && notBlank(email) && notBlank(phone) && dateOfBirth != null
                && notBlank(addressLine1) && notBlank(city) && notBlank(state) && notBlank(postalCode) && notBlank(country);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
