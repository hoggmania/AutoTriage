# AutoTriage — Architecture (One-Pager)

**What it is:** A Temporal-driven, CI-separated security-scanning platform. It runs
OpenGrep SAST on a schedule/PR cadence, applies signed suppression bundles, uses an
optional LLM (ZeroFalse) only to *filter false positives*, and routes low-confidence
findings into a human-reviewed triage workflow that automates signed suppression PRs.
Goal: keep expensive LLM adjudication out of the build gate while still shipping
trustworthy, gated scan results.

**Baseline:** Maven multi-module monorepo · Java 17 · Quarkus · Temporal namespace
`scan-platform` · config via env vars (TLS-ready).

---

## Scan pipeline (happy path)

```
Client → POST /scans (scan-api)
   → Temporal: OpenGrepPRScanWorkflow (queue: scan-workflows)
       1. resolveRepoSource        (light)  → tarball → S3
       2. fetchSuppressionBundle   (light)  → PR head, else base
       3. verifySuppressionSignature (light) → FAIL-CLOSED: bad sig ⇒ no suppressions
       4. runOpenGrep              (opengrep, heavy, heartbeat)
       5. applySuppressions        (filter) + optional ZeroFalse LLM FP adjudication
            → low-confidence candidates → POST /triage/candidates
       6. uploadResults            (light)  → suppression-service
       7. computeVerdict           (light)  → PASS/FAIL gate
   → status queryable via GET /scans/{runId}
```

## Triage workflow (human-in-the-loop)

```
Security Reviewer → triage-service (JWT role: security:vuln_assessor:triager)
   GET /triage/findings  → only "Potential False Positives" persisted
   claim / approve / deny  → repo-keyed audit event
   on approve → clone default branch (HTTPS) → append .opengrep/suppressions/*.yaml
                + .sig placeholder → push branch autotriage/suppressions/{id}
                → optional PR URL via TRIAGE_PR_URL_TEMPLATE
```

---

## Modules

| Module | Role | Temporal queue |
|---|---|---|
| `scan-common` | Shared DTOs, workflow/activity interfaces, artifact + identity + engine + evidence abstractions | — |
| `scan-api` | Quarkus REST entry (`/scans`, `/health`); starts workflows | — |
| `scan-worker-workflow` | Hosts `OpenGrepPRScanWorkflowImpl`; orchestrates the graph | `scan-workflows` |
| `scan-worker-light` | Repo resolve, suppression fetch/verify, upload, verdict | `scan-light` |
| `scan-worker-filter` | SARIF suppression + optional ZeroFalse LLM adjudication | `scan-filter` |
| `scan-worker-opengrep` | Heavy OpenGrep execution (heartbeat, short retries) | `scan-opengrep` |
| `scan-artifact-s3` | `S3ArtifactStore` — S3-compatible object store for tarballs + SARIF | — |
| `scan-identity-kms` | `KmsSigningIdentity` + `KmsSignatureVerifier` — suppression-bundle integrity | — |
| `triage-service` | Postgres-backed CEL policy eval, JWT-secured review API, UI, Git-HTTPS PR automation | — |
| `suppression-service-mock` | Mock `/ingest` endpoint for local/integration flows | — |

`scan-common` also carries a pluggable **engine registry** (`AnalysisEngine`,
`EngineDescriptor`) and an **evidence-calibration** subsystem — the scaffolding behind
ZeroFalse confidence scoring, kept out of the trust-critical gate path.

---

## Design & security posture

- **LLM is advisory, not authoritative.** ZeroFalse only reduces noise; gating verdict is
  deterministic (severity counts + thresholds). The model never holds build credentials.
- **Fail-closed suppressions.** Invalid/missing signature ⇒ no suppressions applied.
- **Repo-managed policy.** Triage classification comes from `.autotriage/policy.cel` on
  the default branch (input narrowed to `cweId` + `confidencePercent`; bands 0–30 TP,
  30–60 potential FP, 61–100 FP), cached by `TRIAGE_POLICY_CACHE_MINUTES`.
- **Least-privilege review.** Triage actions require JWT `role = security:vuln_assessor:triager`.
- **Signed artifacts.** Suppression bundles verified; PRs carry a `.sig` (placeholder until
  real KMS signing lands).

---

## Deployment

- **Containers:** per-module Dockerfiles in `docker/` (Java 17 runtime).
- **Local:** `scripts/podman-compose.{sh,ps1}` — scan-api :8080, suppression-mock :8090,
  triage :8095, Postgres :5432; shared `ARTIFACTS_DIR` volume.
- **K8s:** Kustomize `base` + `overlays/dev` + `overlays/production` (HPA, PDBs,
  NetworkPolicies, RuntimeClass, egress controls). Scan workers/services covered.

---

## Known gaps (do not assume done)

- **Triage service + Postgres k8s manifests are not yet committed** (per `plan.md`/`runbook.md`).
- **`.sig` is a placeholder** — real KMS signing/verification still pending.
- **Production hardening** tracked separately in
  `docs/plans/2026-07-28-production-hardening.md`.
- Triage service uses Git-HTTPS clone with `GIT_CLONE_TOKEN`; no SCM-specific API beyond generic Git.

**Authoritative detail:** `docs/architecture.md` (full prose + mermaid), `docs/runbook.md`,
`docs/triage-plan.md`, `docs/decisions/decision-log.md`.
