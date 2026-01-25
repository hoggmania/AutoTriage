# Plan

We will add a triage workflow that persists low-confidence findings, classifies them via a repo-managed CEL policy (default branch) using only `cweId` and `confidencePercent`, and provides JWT-secured Swagger APIs plus a Security Role UI with Git-over-HTTPS suppression PR automation targeting the default branch.

## Scope
- In: ZeroFalse confidence percent output, CEL policy evaluation with default thresholds, Postgres persistence, Swagger API, JWT auth with `role` claim value `secuirty:vuln_assessor:triager`, Security Role UI, Git-over-HTTPS PR flow, repo-keyed audit trail, docs/ADR updates.
- Out: Non-CEL policy engines, non-JWT auth systems, SCM-specific API integrations beyond generic Git HTTPS.

## Action items
[ ] Extend ZeroFalse parsing/storage to include `confidencePercent` (0–100) with `cweId` and verdict metadata.
[ ] Define the repo-managed CEL policy file on the default branch (e.g., `.autotriage/policy.cel`) with thresholds: 0–30 True Positive, 30–60 Potential False Positive, 61–100 False Positive; add loader/validator.
[ ] Add a triage service module with Postgres migrations/entities for findings, classification, triage claims, approvals/denials, PR status, and repo-keyed audit events.
[ ] Implement a CEL evaluator that consumes only `cweId` + `confidencePercent` and persists the resulting classification.
[ ] Build JWT-secured Swagger APIs for listing/claiming/approving/denying findings and querying audits by repo, enforcing `role=secuirty:vuln_assessor:triager`.
[ ] Build a minimal Security Role UI to claim triage, approve/deny per commit, and view confidence + classification + PR state.
[ ] Implement Git-over-HTTPS suppression updates: fetch signed suppression bundle, append approvals, re-sign, open PR to default branch, log audit entries.
[ ] Add tests: CEL policy unit tests, Postgres integration tests, API auth tests, and a smoke flow from triage candidate to PR creation.
[ ] Update `docs/architecture.md`, `docs/runbook.md`, and the decision log to reflect the triage service, CEL policy, and JWT requirements.

## Open questions
- None.
