package com.insurance.auth.repository;

import com.insurance.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Admin search; a derived query is enough here (single optional filter). */
    Page<User> findByEmailContainingIgnoreCase(String emailFragment, Pageable pageable);
}
