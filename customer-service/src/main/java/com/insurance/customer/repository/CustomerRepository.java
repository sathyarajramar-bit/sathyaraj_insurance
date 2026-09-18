package com.insurance.customer.repository;

import com.insurance.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/** {@link JpaSpecificationExecutor} adds {@code findAll(Specification, Pageable)} for the admin search. */
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByUserId(Long userId);

    boolean existsByEmailIgnoreCase(String email);
}
