package com.autotriage.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class TriageCandidateResponse {
    private final TriageClassification classification;
    private final boolean stored;
    private final String findingId;

    @JsonCreator
    public TriageCandidateResponse(
            @JsonProperty("classification") TriageClassification classification,
            @JsonProperty("stored") boolean stored,
            @JsonProperty("findingId") String findingId) {
        this.classification = classification;
        this.stored = stored;
        this.findingId = findingId;
    }

    public TriageClassification getClassification() {
        return classification;
    }

    public boolean isStored() {
        return stored;
    }

    public String getFindingId() {
        return findingId;
    }
}
