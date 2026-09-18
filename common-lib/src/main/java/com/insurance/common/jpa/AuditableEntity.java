package com.insurance.common.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Audit columns and optimistic locking shared by every entity in the platform.
 *
 * <p>Interview notes:
 * <ul>
 *   <li>{@code @MappedSuperclass}: the columns are inherited by each entity's table; there is no table
 *       for this class and no join.</li>
 *   <li>{@code AuditingEntityListener} fills the four audit fields from the {@code AuditorAware} bean
 *       (the JWT user id, or "system" for jobs).</li>
 *   <li>{@code @Version}: Hibernate adds {@code WHERE version = ?} to every UPDATE and increments it.
 *       Two concurrent edits of the same row: the second one updates 0 rows and Hibernate throws
 *       {@code OptimisticLockException} (mapped to 409). No database locks are held while the user thinks.</li>
 * </ul>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
