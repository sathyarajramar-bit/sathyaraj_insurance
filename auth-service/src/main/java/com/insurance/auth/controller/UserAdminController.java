package com.insurance.auth.controller;

import com.insurance.auth.dto.AssignRolesRequest;
import com.insurance.auth.dto.UserResponse;
import com.insurance.auth.service.UserService;
import com.insurance.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only user management. Method security ({@code @PreAuthorize}) is evaluated on the proxy before
 * the controller method runs; a CUSTOMER token gets 403 from the shared handlers.
 */
@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User administration", description = "ADMIN only")
public class UserAdminController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Search users by email fragment, paginated: ?email=&page=0&size=20&sort=createdAt,desc")
    public PageResponse<UserResponse> search(
            @RequestParam(required = false) String email,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return userService.search(email, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by id")
    public UserResponse get(@PathVariable Long id) {
        return userService.getById(id);
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Replace a user's roles (e.g. promote to AGENT)")
    public UserResponse assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        return userService.assignRoles(id, request.roles());
    }
}
