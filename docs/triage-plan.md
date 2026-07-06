# Plan

We will add a triage workflow that persists low-confidence findings, classifies them via a repo-managed CEL policy (default branch) using only `cweId` and `confidencePercent`, and provides JWT-secured Swagger APIs plus a Security Role UI with Git-over-HTTPS suppression PR automation targeting the default branch.

## Scope
- In: ZeroFalse confidence percent output, CEL policy evaluation with default thresholds, Postgres persistence, Swagger API, JWT auth with `role` claim value `security:vuln_assessor:triager`, Security Role UI, Git-over-HTTPS PR flow, repo-keyed audit trail, docs/ADR updates.
- Out: Non-CEL policy engines, non-JWT auth systems, SCM-specific API integrations beyond generic Git HTTPS.

## Action items
- [x] Extend ZeroFalse parsing/storage to include `confidencePercent` (0-100) with `cweId` and verdict metadata.
- [x] Define the repo-managed CEL policy file on the default branch (e.g., `.autotriage/policy.cel`) with thresholds: 0-30 True Positive, 30-60 Potential False Positive, 61-100 False Positive; add loader/validator.
- [x] Add a triage service module with Postgres migrations/entities for findings, classification, triage claims, approvals/denials, PR status, and repo-keyed audit events.
- [x] Implement a CEL evaluator that consumes only `cweId` + `confidencePercent` and persists the resulting classification.
- [x] Build JWT-secured Swagger APIs for listing/claiming/approving/denying findings and querying audits by repo, enforcing `role=security:vuln_assessor:triager`.
- [x] Build a minimal Security Role UI to claim triage, approve/deny per commit, and view confidence + classification + PR state.
- [x] Implement Git-over-HTTPS suppression updates: append suppression entries under `.opengrep/suppressions/*.yaml`, write a `.sig` placeholder, push a branch, and optionally format PR URLs via `TRIAGE_PR_URL_TEMPLATE`; log audit entries.
- [x] Add tests: CEL policy unit tests plus Quarkus tests for candidate ingest, approval flow, and JWT auth (H2-backed; Quarkus tests are skipped on Java 25).
- [x] Update `docs/architecture.md`, `docs/runbook.md`, and the decision log to reflect the triage service, CEL policy, and JWT requirements.

## Implementation notes
- The triage service stores only Potential False Positives; other classifications are returned to the filter worker without persistence.
- Policy loading reads `.autotriage/policy.cel` from the repo default branch, cached by `TRIAGE_POLICY_CACHE_MINUTES`.
- PR automation writes suppression updates to `.opengrep/suppressions` and uses a placeholder signature until real signing is added.

## Open questions
- None.
