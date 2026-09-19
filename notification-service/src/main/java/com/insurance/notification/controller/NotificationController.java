package com.insurance.notification.controller;

import com.insurance.common.dto.PageResponse;
import com.insurance.common.notification.NotificationRequest;
import com.insurance.notification.dto.NotificationResponse;
import com.insurance.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications", description = "Customer messages (email/SMS) and their delivery status")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(summary = "Internal: accept an event from another service (idempotent by idempotencyKey + channel)")
    public ResponseEntity<List<NotificationResponse>> accept(@Valid @RequestBody NotificationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(notificationService.accept(request));
    }

    @GetMapping
    @Operation(summary = "My notifications; staff may filter by ?referenceType=&referenceNumber=")
    public PageResponse<NotificationResponse> list(@RequestParam(required = false) String referenceType,
                                                   @RequestParam(required = false) String referenceNumber,
                                                   @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (referenceType != null && referenceNumber != null) {
            return notificationService.listByReference(referenceType, referenceNumber, pageable);
        }
        return notificationService.listMine(pageable);
    }
}
