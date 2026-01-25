Here’s a **CODEX execution plan** for building the **self-hosted Temporal + Quarkus Scan API + containerized Activity workers + external Suppression Service + Security Dashboard + signed suppression PR** system (Option 1), with explicit instructions to **keep a decision record**.

You can paste this directly into Codex as a “project brief / task plan”.

For the triage workflow, see `docs/triage-plan.md`.

---

## CODEX Plan: Temporal OpenGrep Scan Platform (Quarkus/Java) with Decision Log

### Operating rules for Codex

1. **Maintain a decision log** at `docs/decisions/decision-log.md`.

   * Add an entry whenever you choose between alternatives (API shape, task queues, signature format, storage schema, retry policies, etc.).
   * Each entry must include:

     * **Context**
     * **Decision**
     * **Options considered**
     * **Rationale**
     * **Consequences**
     * **Date**
2. Maintain an **Architecture doc** at `docs/architecture.md` and keep it current.
3. Every service must have:

   * `/health` endpoint (where applicable)
   * structured logging
   * config via environment variables
4. Prefer **small, incremental PR-sized commits** (even if you’re working locally), with commit messages referencing decision log entries when relevant.

---

## Phase 0 — Repo skeleton and shared conventions

**Goal:** create a mono-repo with multi-module Maven structure and baseline docs.

### Deliverables

* Maven multi-module structure:

  * `scan-common/` (DTOs, workflow/activity interfaces)
  * `scan-api/` (Quarkus REST; starts workflows; query status)
  * `scan-worker-workflow/` (Temporal workflow worker)
  * `scan-worker-light/` (activities: repo resolve, suppression fetch/verify, upload, compute verdict)
  * `scan-worker-filter/` (activity: SARIF suppression application)
  * `scan-worker-opengrep/` (activity: run OpenGrep; heavy container)
* Docs:

  * `docs/architecture.md`
  * `docs/decisions/decision-log.md` (initialize)
  * `docs/runbook.md` (how to run locally + in k8s)
* `docker/` folder with Dockerfiles for each worker and scan-api.
* Basic GitHub Actions workflow for unit tests + formatting.

### Decision log entries to record

* ADR-0001: monorepo + module split (why split workers by task queue)
* ADR-0002: DTO serialization choice (Jackson JSON; no protobuf for now)

---

## Phase 1 — Temporal connectivity (self-hosted) + mTLS-ready configuration

**Goal:** connect Quarkus apps to self-hosted Temporal, with config that supports both plaintext and mTLS.

### Deliverables

* `scan-common`: Temporal interfaces

  * `OpenGrepPRScanWorkflow` (run + status query + cancel signal)
  * `ScanActivities` interfaces (or split into Light/Heavy/Filter if task-queue config needs it)
* `scan-api`: `WorkflowClient` producer with config:

  * `temporal.target`
  * `temporal.namespace`
  * `temporal.tls.enabled`
  * `temporal.tls.client-cert-path`, `client-key-path`, `server-ca-path`
* `scan-worker-workflow`: Worker bootstrap for `scan-workflows` queue
* Local dev: docker-compose or docs for pointing to an existing Temporal cluster

### Decision log entries

* ADR-0003: Temporal namespace(s) strategy (use `scan-platform`)
* ADR-0004: TLS vs no TLS defaults; how certs are mounted

---

## Phase 2 — Scan API (ASTCLI-facing) with workflow Query-based status

**Goal:** implement the REST API that ASTCLI calls.

### Endpoints

* `POST /scans`

  * body: `ScanRequest`
  * returns: `ScanStatus{ runId, state=RUNNING }`
* `GET /scans/{runId}`

  * returns `ScanStatus` from workflow query
* `POST /scans/{runId}/cancel`

  * signals workflow cancel

### Deliverables

* Request validation
* Deterministic `workflowId = "opengrep-scan-" + runId`
* Error handling for “workflow not found”
* `ScanStatus` model (RUNNING/COMPLETED/FAILED/CANCELED)
* `/health`

### Decision log entries

* ADR-0005: status source (workflow query first; DB later if needed)

---

## Phase 3 — Workflow orchestration (happy path end-to-end, stubbed activities)

**Goal:** implement `OpenGrepPRScanWorkflowImpl` that calls activities in the correct order with retries/timeouts.

### Workflow steps (must match architecture)

1. `resolveRepoSource`
2. `fetchSuppressionBundle` (PR head, else base)
3. `verifySuppressionSignature` (fail-closed → empty suppressions)
4. `runOpenGrep` (heavy)
5. `applySuppressions`
6. `uploadResults` (to suppression service)
7. `computeVerdict`
8. update internal status for query

### Deliverables

* Proper activity retry policies:

  * light: exponential backoff, more attempts
  * heavy: fewer attempts, heartbeat enabled
* Status updates at each stage (for Query)
* Cancel signal support (set flag, check between steps)

### Decision log entries

* ADR-0006: retry policies per activity type (and why)
* ADR-0007: “fail-closed suppressions” semantics (invalid signature => no suppressions applied)

---

## Phase 4 — Activity workers (real implementations)

### 4.1 Repo resolver activity (light)

**Options**: clone repo directly vs fetch archive.
Implement minimally:

* git clone with GitHub App token
* checkout SHA
* zip/tar workspace and upload to object store (S3-compatible)

**Deliverables**

* `resolveRepoSource` uploads a tarball to object storage, returns `ArtifactRef`

**Decision log**

* ADR-0008: source materialization approach (clone+archive)

### 4.2 Suppression fetch activity (light)

Fetch `.opengrep/suppressions/*` from PR head ref else base ref.
Return `sourceRefUsed = PR_HEAD|BASE_REF|NONE`.

**Decision log**

* ADR-0009: suppression ref resolution rule (prefer PR head)

### 4.3 Signature verify activity (light/security)

Implement:

* canonicalization of YAML (stable key ordering)
* signature verification (pluggable: KMS/Sigstore later)
  For now, implement a placeholder verifier with a test public key, but structure it behind an interface.

**Decision log**

* ADR-0010: signature format + canonicalization method (YAML canonicalization rules)

### 4.4 OpenGrep runner activity (heavy container)

Implement:

* download source archive
* run OpenGrep in container
* output SARIF (raw)
* upload to object store
* heartbeat periodically while running

**Decision log**

* ADR-0011: OpenGrep invocation strategy (configRef resolution, command-line flags)

### 4.5 SARIF filter activity (filter worker)

Implement:

* download `raw.sarif`
* if verified suppressions => apply filtering:

  * match by sarif fingerprint preferred
  * fallback region+hash
  * enforce TTL
  * record stale/expired/invalid counts
* upload `final.sarif` + `suppressionReport.json`

**Decision log**

* ADR-0012: matching precedence and drift detection algorithm

### 4.6 Uploader activity (light)

Implement call to Suppression Service:

* send run metadata + artifact URIs (raw & final)
* receive report/dashboard/pr links

**Decision log**

* ADR-0013: uploader contract (send URIs vs send SARIF bodies)

### 4.7 Verdict activity (light)

Compute PASS/FAIL based on:

* final findings count by severity
* configured thresholds in `gatePolicyRef`

**Decision log**

* ADR-0014: gating policy structure

---

## Phase 5 — Minimal “Suppression Service” contract (mock-first)

Since your suppression service/dashboard might be separate work, Codex should:

* implement uploader against a **mock endpoint** first
* provide a small `suppression-service-mock/` (optional) to unblock integration tests

**Deliverables**

* `POST /ingest` mock accepts run payload and returns links

**Decision log**

* ADR-0015: mock contract details

---

## Phase 6 — Build/run packaging (Docker + k8s manifests)

**Deliverables**

* Dockerfiles for scan-api and each worker module
* K8s manifests:

  * Deployments for:

    * scan-api
    * workflow-worker
    * light-worker
    * filter-worker
    * opengrep-heavy-worker
  * ConfigMaps/Secrets for Temporal endpoint + TLS cert paths
  * HPA for opengrep-heavy-worker
* Example NetworkPolicies (optional)

**Decision log**

* ADR-0016: worker deployment split and resource sizing defaults

---

## Phase 7 — Observability and ops hardening

**Deliverables**

* Structured logs with `runId`, `repo`, `prNumber`, `sha`
* Metrics (at least):

  * workflow success/failure
  * activity retries/failures
  * scan duration
  * findings counts raw/final
* Runbook updates:

  * how to scale workers
  * how to debug stuck workflows (Temporal UI + queries)
  * how to rotate certs

**Decision log**

* ADR-0017: metrics library choice (Micrometer + Prometheus)

---

## Definition of Done (for Codex)

* A PR scan can be started via Scan API and completes end-to-end:

  * runs OpenGrep in heavy activity container
  * applies suppressions if signature valid
  * uploads results to mock suppression service
  * returns PASS/FAIL to status query
* Decision log is updated for each significant choice.
* Docs reflect the implementation, task queues, and configuration.

---

## Files Codex must create/update

* `docs/decisions/decision-log.md` (append entries)
* `docs/architecture.md`
* `scan-common/src/main/java/...` (DTOs + interfaces)
* `scan-api/src/main/java/...` (REST + Temporal client)
* `scan-worker-*/src/main/java/...` (workers + activities)
* `docker/*` (Dockerfiles)
* `k8s/*` (manifests)

---

