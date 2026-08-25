package com.autotriage.triage.policy;

import com.autotriage.common.model.TriageClassification;
import com.autotriage.common.evidence.TriageEvidence;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TriagePolicyService {

    private static final Logger log = Logger.getLogger(TriagePolicyService.class);

    private final RepoPolicyLoader policyLoader;
    private final CelPolicyEvaluator evaluator;
    private final Map<String, CachedPolicy> cache = new ConcurrentHashMap<>();

    @Inject
    public TriagePolicyService(RepoPolicyLoader policyLoader, CelPolicyEvaluator evaluator) {
        this.policyLoader = policyLoader;
        this.evaluator = evaluator;
    }

    public TriageClassification classify(String repository, String cweId, TriageEvidence evidence) {
        if (evidence == null || !evidence.getProvenance().isCompleteForPolicy()) {
            return TriageClassification.TRUE_POSITIVE;
        }
        String policy = loadPolicy(repository);
        String result = evaluator.evaluate(policy, cweId, evidence);
        TriageClassification classification = mapClassification(result);
        if (classification != null) {
            return classification;
        }
        return defaultClassification(evidence);
    }

    private String loadPolicy(String repository) {
        if (repository == null || repository.isBlank()) {
            return defaultPolicy();
        }
        CachedPolicy cached = cache.get(repository);
        if (cached != null && !cached.isExpired()) {
            return cached.policy();
        }
        String loaded = policyLoader.loadPolicy(repository);
        if (loaded == null || loaded.isBlank()) {
            loaded = defaultPolicy();
        }
        cache.put(repository, new CachedPolicy(loaded, Instant.now(), policyTtl()));
        return loaded;
    }

    private Duration policyTtl() {
        int minutes = ConfigProvider.getConfig()
                .getOptionalValue("triage.policy.cache-minutes", Integer.class)
                .orElse(5);
        return Duration.ofMinutes(Math.max(1, minutes));
    }

    private String defaultPolicy() {
        return "evidence.level == 'STRONG' ? 'FALSE_POSITIVE' : "
                + "evidence.level == 'MODERATE' ? 'POTENTIAL_FALSE_POSITIVE' : 'TRUE_POSITIVE'";
    }

    private TriageClassification mapClassification(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "TRUE_POSITIVE" -> TriageClassification.TRUE_POSITIVE;
            case "POTENTIAL_FALSE_POSITIVE", "POTENTIAL_FALSE_POSIVE" -> TriageClassification.POTENTIAL_FALSE_POSITIVE;
            case "FALSE_POSITIVE" -> TriageClassification.FALSE_POSITIVE;
            default -> null;
        };
    }

    private TriageClassification defaultClassification(TriageEvidence evidence) {
        return switch (evidence.getCalibratedLevel()) {
            case STRONG -> TriageClassification.FALSE_POSITIVE;
            case MODERATE -> TriageClassification.POTENTIAL_FALSE_POSITIVE;
            case LIMITED, INSUFFICIENT -> TriageClassification.TRUE_POSITIVE;
        };
    }

    private record CachedPolicy(String policy, Instant loadedAt, Duration ttl) {
        boolean isExpired() {
            return loadedAt.plus(ttl).isBefore(Instant.now());
        }
    }
}
