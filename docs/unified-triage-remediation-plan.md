# Unified Triage and Remediation — Implementation Plan

**Status:** Proposed; no implementation changes. **Date:** 2026-10-24.

## Objective

Extend AutoTriage's OpenGrep + Temporal + CEL + governed suppression architecture with **one unified LLM triage-and-remediate action**. Minimise duplicated model context and cost. Every unsuppressed finding should yield a candidate patch, evidence-backed false-positive review, or explicit reason that automated remediation was not achieved. Preserve the original scan/gate verdict.

## Target workflow

1. Existing `POST /scans` and `OpenGrepPRScanWorkflow`: pin repository commit, verify suppression bundle, run OpenGrep, produce SARIF including source/sink/taint trace.
2. Apply verified suppressions, deduplicate and group related findings by root cause; preserve original finding IDs.
3. Asynchronously dispatch eligible findings/clusters to a separate Temporal remediation child workflow/task queue, **without blocking CI**.
4. Assemble bounded context: SARIF rule/CWE, source/sink, taint trace, relevant functions/callers, tests and repo metadata. Track token budget and provenance.
5. **Single unified LLM action**: assess finding and generate minimal safe patch unless confidently false. If uncertain, attempt safe remediation anyway. Return structured evidence.
6. Route: confident false positive → existing suppression review (never auto-suppress); patch generated → isolated validation; no automated fix → manual queue with reason; inconclusive → bounded context expansion or review.
7. Apply patch in disposable Git worktree; enforce diff limits, allowed paths, no privileged credentials.
8. Independently validate: build, tests, OpenGrep rescan using pinned unchanged rules/config; compare original and equivalent source-to-sink flows and newly introduced findings. A cleared scanner finding does not prove universal exploit safety.
9. On success, create **draft** remediation PR with evidence. On failure, retry with test/rescan feedback within strict limits; then escalate. No automatic merges.
10. Persist evidence, outcomes, policy decisions, artifacts, versions, costs and audit trail.

## Structured outcome model

Keep triage, remediation and validation separate:

- `triageVerdict: TRUE_POSITIVE | FALSE_POSITIVE | UNCERTAIN`
- `triageConfidencePercent`, explanation, evidence
- `remediationStatus: FIX_GENERATED | NO_AUTOMATED_FIX_FOUND | FIX_NOT_ATTEMPTED | ANALYSIS_INCONCLUSIVE`
- `validationStatus: NOT_RUN | PASSED | FAILED | ERROR`
- `workflowOutcome: DRAFT_PR_CREATED | SUPPRESSION_REVIEW | MANUAL_REMEDIATION | RETRY_PENDING | FIX_FAILED`
- `patchArtifactUri`, `validationArtifactUri`, `manualAction`, original finding IDs, commit SHA, ruleset version, model/prompt version, attempts, tokens, cost

**NO_AUTOMATED_FIX_FOUND** means no safe automated fix was identified, **not** that no fix exists. **FIX_FAILED** means a candidate failed validation. Neither closes or suppresses the finding. Inconclusive findings stay open.

## Module changes

| Module | Proposed change |
| --- | --- |
| `scan-common` | DTOs, enums, engine SPI and Temporal interfaces |
| `scan-api` | Backward-compatible remediation options and status endpoints |
| `scan-worker-workflow` | Separate child workflow, queue, retries, budgets and concurrency |
| `scan-worker-filter` | Retain suppression filtering; keep ZeroFalse optional for A/B comparison |
| `scan-worker-opengrep` | Reuse for post-patch rescans with pinned configuration |
| `scan-worker-light` | Artifact publishing, audit metadata and verdict separation |
| `scan-artifact-s3` | Store patches, pre/post SARIF, model evidence and test reports |
| `triage-service` | Persist **all** remediation outcomes, manual queue, reviewer actions |
| New `scan-worker-remediation` | Code-context builder, unified LLM, sandbox patch and validation execution |

Introduce **separate remediation CEL policy**, not an incompatible extension of current `.autotriage/policy.cel` (`cweId`, `confidencePercent`). Feature-flag new workflow off by default.

OpenAnt is a potential backend behind the engine SPI, **not** a mandatory second full scan. Verify its ability to accept external SARIF findings, supported languages, licensing and reusable interfaces. Its Auto Patcher is not assumed to expose a generic SARIF-to-patch API.

## Delivery plan

### PR 1 — Contracts and governance (P0)
- [ ] DTOs, statuses, durable records for all outcomes and migrations
- [ ] Remediation policy, feature flag, backward-compatible API
- [ ] State-machine, schema, policy and migration tests

### PR 2 — Unified agent (P0)
- [ ] SARIF taint-context extraction and bounded code slices
- [ ] Single-request triage + patch schema, strict response validation
- [ ] Provider-agnostic engine SPI, token budgets and evidence provenance
- [ ] Isolated worker and Temporal child workflow

### PR 3 — Patch validation and PRs (P0/P1)
- [ ] Disposable worktree, diff/path limits and sandbox isolation
- [ ] Build/tests and pinned OpenGrep rescan
- [ ] Compare taint paths; reject new high-severity findings
- [ ] Bounded retry and explicit no-fix/failure outcomes
- [ ] Draft PR via separately privileged publishing service

### PR 4 — Optimisation and evaluation (P1/P2)
- [ ] Root-cause clustering and overlapping patch prevention
- [ ] A/B benchmark against ZeroFalse-first and direct-fix paths
- [ ] OpenAnt adapter feasibility spike
- [ ] Metrics: cost per validated merged fix, triage error, patch success, review acceptance
- [ ] Optional scheduled deep discovery beyond OpenGrep coverage

## Acceptance tests

1. Confident false positive → suppression **review**, no automatic signing.
2. Confirmed taint vulnerability → candidate patch, rescan clears original source-to-sink path.
3. Uncertain but safely fixable → remediation attempted and validated.
4. Architectural issue → `NO_AUTOMATED_FIX_FOUND` plus manual action; remains open.
5. Patch fails build, retains equivalent taint path or introduces new severe finding → fail, bounded retry/escalation.
6. Scanner configuration weakened or source/sink rules removed → reject validation.
7. Untrusted repository prompt injection cannot access secrets or privileged GitHub token.
8. LLM unavailable/budget exhausted → scan verdict unaffected.
9. Remediation disabled → current scan/triage behaviour unchanged.

## Security and compatibility notes

Repository source and build output are untrusted; sandbox execution with restricted network and no production secrets. Pin commit/rules/model/prompt. Never auto-merge. Existing suppression PR documentation describes a placeholder `.sig`; production trust requires real signing and verification. Do not equate scanner-cleared with exploit-proof.

## References

- [Architecture](architecture.md)
- [Existing triage plan](triage-plan.md)
- [README](../README.md)
