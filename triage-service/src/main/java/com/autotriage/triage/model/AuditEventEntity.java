package com.autotriage.triage.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "triage_audit_events")
public class AuditEventEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "repository", nullable = false)
    public String repository;

    @Column(name = "finding_id")
    public UUID findingId;

    @Column(name = "event_type", nullable = false)
    public String eventType;

    @Column(name = "actor")
    public String actor;

    @Column(name = "details")
    public String details;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
