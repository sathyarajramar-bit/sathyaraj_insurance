package com.insurance.auth.dto;

import com.insurance.auth.entity.Role;
import com.insurance.auth.entity.UserStatus;

import java.time.Instant;
import java.util.Set;

public record UserResponse(Long id, String email, String firstName, String lastName, String phone,
                           UserStatus status, Set<Role> roles, Instant createdAt) {
}
