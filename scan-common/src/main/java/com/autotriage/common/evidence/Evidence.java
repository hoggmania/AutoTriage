package com.autotriage.common.evidence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@JsonPropertyOrder({"id", "type", "level", "summary", "provenance", "attributes"})
public final class Evidence {
    private final String id;
    private final String type;
    private final FindingSeverity level;
    private final String summary;
    private final EvidenceProvenance provenance;
    private final Map<String, String> attributes;

    @JsonCreator
    public Evidence(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type,
            @JsonProperty("level") FindingSeverity level,
            @JsonProperty("summary") String summary,
            @JsonProperty("provenance") EvidenceProvenance provenance,
            @JsonProperty("attributes") Map<String, String> attributes) {
        this.id = requireText(id, "id");
        this.type = requireText(type, "type");
        this.level = Objects.requireNonNull(level, "level");
        this.summary = requireText(summary, "summary");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.attributes = immutableMap(attributes, "attributes");
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public FindingSeverity getLevel() { return level; }
    public String getSummary() { return summary; }
    public EvidenceProvenance getProvenance() { return provenance; }
    public Map<String, String> getAttributes() { return attributes; }

    private static Map<String, String> immutableMap(Map<String, String> values, String name) {
        Objects.requireNonNull(values, name);
        TreeMap<String, String> copy = new TreeMap<>();
        values.forEach((key, value) -> copy.put(requireText(key, name + " key"), requireText(value, name + " value")));
        return Collections.unmodifiableMap(copy);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Evidence that)) return false;
        return id.equals(that.id) && type.equals(that.type) && level == that.level
                && summary.equals(that.summary) && provenance.equals(that.provenance)
                && attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, level, summary, provenance, attributes);
    }
}
