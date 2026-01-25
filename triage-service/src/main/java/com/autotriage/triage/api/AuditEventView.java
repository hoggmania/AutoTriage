package com.autotriage.triage.api;

import com.autotriage.triage.model.AuditEventEntity;

import java.time.Instant;

public final class AuditEventView {
    private final String id;
    private final String repository;
    private final String findingId;
    private final String eventType;
    private final String actor;
    private final String details;
    private final Instant createdAt;

    public AuditEventView(AuditEventEntity entity) {
        this.id = entity.id == null ? null : entity.id.toString();
        this.repository = entity.repository;
        this.findingId = entity.findingId == null ? null : entity.findingId.toString();
        this.eventType = entity.eventType;
        this.actor = entity.actor;
        this.details = entity.details;
        this.createdAt = entity.createdAt;
    }

    public String getId() {
        return id;
    }

    public String getRepository() {
        return repository;
    }

    public String getFindingId() {
        return findingId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getActor() {
        return actor;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
