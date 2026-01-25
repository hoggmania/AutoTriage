package com.autotriage.triage.service;

import com.autotriage.triage.model.AuditEventEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class AuditService {

    @Transactional
    public void record(String repository, UUID findingId, String eventType, String actor, String details) {
        AuditEventEntity event = new AuditEventEntity();
        event.id = UUID.randomUUID();
        event.repository = repository;
        event.findingId = findingId;
        event.eventType = eventType;
        event.actor = actor;
        event.details = details;
        event.createdAt = Instant.now();
        event.persist();
    }
}
