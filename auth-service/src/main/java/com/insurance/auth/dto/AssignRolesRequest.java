package com.insurance.auth.dto;

import com.insurance.auth.entity.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record AssignRolesRequest(@NotEmpty(message = "At least one role is required") Set<Role> roles) {
}
