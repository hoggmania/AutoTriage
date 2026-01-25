# Decision Log

## ADR-0001: Monorepo with worker task-queue split
- **Context:** AutoTriage must orchestrate multiple Temporal workers (light, filter, heavy) and the API while keeping shared DTOs consistent. Phase 0 demands a mono-repo layout that reflects the runtime boundaries.
- **Decision:** Use a Maven multi-module repository with discrete modules for each worker and the API, each bound to their own Temporal task queue.
- **Options considered:** (1) Single-module codebase with runtime branching, (2) Separate repositories per worker, (3) Monorepo multi-module split by capability.
- **Rationale:** Multi-module approach keeps shared DTOs/flows in `scan-common` while allowing different dependencies, retries, and deployment cadence per worker. Single module makes deployment packaging harder; multiple repos complicate shared code.
- **Consequences:** Developers can build the entire platform locally via the parent POM, but deployments can target each module separately; CI must build/test modules collectively.
- **Date:** 2026-01-24

## ADR-0002: DTO serialization via Jackson JSON
- **Context:** Temporal workflows need to exchange domain objects between API, workflow, and activity workers while keeping the stack simple for Phase 0.
- **Decision:** Use Jackson JSON serialization for DTOs and workflow/activity interfaces; defer protobuf or other binary formats until real telemetry/performance needs emerge.
- **Options considered:** (1) Jackson JSON (default Quarkus/Java support), (2) Protocol Buffers (more compact but adds schema tooling), (3) Pure Java serialization (legacy and brittle).
- **Rationale:** Jackson integrates natively with Quarkus and Temporal SDKs, has no extra schema compilation step, and is well-understood by JVM teams.
- **Consequences:** Serialization is human-readable and compatible with Quarkus/Temporal defaults; future migrations to protobuf will require schema conversion and compatibility modeling.
- **Date:** 2026-01-24

## ADR-0003: Temporal namespace strategy
- **Context:** Phase 1 requires connecting all services to a shared Temporal deployment while keeping workflow identity and operator workflows simple.
- **Decision:** Use a single Temporal namespace named `scan-platform` for all scan workflows and activities.
- **Options considered:** (1) Single namespace for all services, (2) Separate namespace per environment or worker type, (3) Namespace per repository/tenant.
- **Rationale:** A single namespace keeps the initial wiring simple and matches the Phase 1 scope; environment separation can be handled via separate Temporal clusters or namespaces later.
- **Consequences:** Operational tooling and namespace-level quotas are shared; future multi-tenant needs will require namespace expansion or routing.
- **Date:** 2026-01-24

## ADR-0004: TLS defaults for Temporal connectivity
- **Context:** The platform must support both plaintext and mTLS connections to self-hosted Temporal clusters.
- **Decision:** Default to plaintext (`temporal.tls.enabled=false`) and enable mTLS via env var plus mounted certificate paths when required.
- **Options considered:** (1) TLS-on by default with mandatory certs, (2) TLS optional with explicit enablement, (3) Separate binaries for TLS vs non-TLS.
- **Rationale:** Optional TLS keeps local dev simple while still meeting production requirements by toggling environment configuration.
- **Consequences:** Operators must explicitly enable TLS in production manifests; misconfiguration can lead to plaintext connections if env vars are missing.
- **Date:** 2026-01-24

## ADR-0005: Scan status source
- **Context:** The Scan API must report status for in-flight scans without a backing database in Phase 2.
- **Decision:** Use the Temporal workflow query as the source of truth for scan status; defer persistence to a database for later phases.
- **Options considered:** (1) Workflow query-only, (2) Workflow + external DB projection, (3) DB-first with Temporal for orchestration only.
- **Rationale:** Query-based status is immediately available without additional infrastructure, aligning with the incremental rollout plan.
- **Consequences:** Status is volatile if workflows are evicted or histories are purged; future DB integration will require dual-write or async projection.
- **Date:** 2026-01-24

## ADR-0006: Activity retry policies by worker type
- **Context:** The workflow executes light, filter, and heavy OpenGrep activities with different runtime profiles and failure modes.
- **Decision:** Use more aggressive retries with exponential backoff for light activities, moderate retries for filter activities, and limited retries with heartbeats for heavy OpenGrep execution.
- **Options considered:** (1) Uniform retry policy for all activities, (2) Per-activity tuned retry policies, (3) No retries with manual intervention.
- **Rationale:** Tailoring retries balances throughput and cost: light tasks can be retried cheaply, while heavy tasks should fail fast and rely on heartbeats for progress.
- **Consequences:** Retry configurations must be kept in sync with operational expectations; observability should track retries by task queue.
- **Date:** 2026-01-24

## ADR-0007: Fail-closed suppressions
- **Context:** Suppression bundles may be missing or have invalid signatures, and the workflow must decide whether to apply them.
- **Decision:** If suppression verification fails, treat suppressions as empty and continue (fail-closed).
- **Options considered:** (1) Fail-closed (ignore invalid suppressions), (2) Fail-open (apply regardless), (3) Stop workflow on verification failure.
- **Rationale:** Fail-closed ensures untrusted suppressions do not hide findings, while still allowing the scan to complete.
- **Consequences:** Some runs may report more findings until suppression signatures are fixed; alerting should highlight verification failures.
- **Date:** 2026-01-24

## ADR-0015: Suppression service mock contract
- **Context:** Phase 5 calls for a mock suppression service so the uploader can be exercised before the real service is available.
- **Decision:** Implement a minimal `suppression-service-mock` with `POST /ingest` that accepts any JSON payload and returns runId plus mock report/dashboard/PR URLs.
- **Options considered:** (1) Mock service with a fixed JSON response, (2) In-memory service with minimal validation, (3) Skip mock and stub uploads in code.
- **Rationale:** A lightweight HTTP service mirrors the eventual integration without requiring the full dashboard stack.
- **Consequences:** The mock must be replaced or extended once the real suppression service contract is finalized.
- **Date:** 2026-01-24

## ADR-0013: Uploader contract (URIs vs bodies)
- **Context:** The light worker must transmit scan results to the suppression service while keeping payload size manageable.
- **Decision:** Send artifact URIs (raw and final SARIF) plus run metadata instead of uploading SARIF bodies directly.
- **Options considered:** (1) Send URIs, (2) Send SARIF JSON bodies, (3) Send mixed (final body + raw URI).
- **Rationale:** URIs keep payloads small and allow the suppression service to fetch artifacts from object storage.
- **Consequences:** The suppression service must have access to the object store; failures will surface as fetch errors rather than upload errors.
- **Date:** 2026-01-24

## ADR-0008: Source materialization approach
- **Context:** The light worker must make repository contents available to the OpenGrep worker as an artifact.
- **Decision:** Clone the repository and archive the working tree into a tar.gz artifact.
- **Options considered:** (1) Clone and archive, (2) Download archive from VCS API, (3) On-demand checkout per worker.
- **Rationale:** Clone+archive is straightforward, supports arbitrary refs, and matches the activity sequence in the workflow.
- **Consequences:** Requires git availability on the worker image and temporary disk space for the checkout and archive.
- **Date:** 2026-01-25

## ADR-0018: Local artifact storage in early phases
- **Context:** Object storage integration is pending, but activities need a URI to pass between workers.
- **Decision:** Store artifacts on local disk (configurable via `artifacts.dir`) and return `file://` URIs during early development.
- **Options considered:** (1) Local filesystem storage, (2) In-memory blobs, (3) Immediate S3 integration.
- **Rationale:** Local storage keeps the implementation lightweight while allowing end-to-end workflow testing.
- **Consequences:** Workers must share a filesystem or the artifact store will need to be replaced for distributed deployments.
- **Date:** 2026-01-25

## ADR-0009: Suppression ref resolution
- **Context:** Suppression bundles may differ between the PR head and base ref; the workflow must pick the correct source.
- **Decision:** Prefer the PR head ref when available, otherwise fall back to the base ref; the activity uses the ref passed in by the workflow.
- **Options considered:** (1) PR head first, (2) Base ref only, (3) Merge base and head suppressions.
- **Rationale:** PR head is most relevant to the change under test and matches the requirement in the scan workflow steps.
- **Consequences:** If PR head ref metadata is missing, suppressions are sourced from the base ref; future enhancements may need explicit ref fields.
- **Date:** 2026-01-25

## ADR-0010: Suppression signature format and canonicalization
- **Context:** Suppression bundles must be authenticated before filtering SARIF results.
- **Decision:** Define a detached signature file (`.sig`) stored alongside the suppression bundle, using a canonicalized YAML payload with stable key ordering and normalized line endings for signature input.
- **Options considered:** (1) Detached signature file, (2) Inline signature in YAML, (3) No signature with trusted storage.
- **Rationale:** Detached signatures keep suppression data clean and allow future KMS/Sigstore verification workflows.
- **Consequences:** The light worker currently uses a placeholder verifier and a test signature while the canonicalization and real signing are implemented.
- **Date:** 2026-01-25

## ADR-0011: OpenGrep invocation strategy
- **Context:** The heavy worker must run OpenGrep against the materialized source with a configurable rule set.
- **Decision:** Invoke an `opengrep` binary with `--config`, `--sarif`, and `--output` flags; fall back to emitting a stub SARIF when the binary/config is not set.
- **Options considered:** (1) Direct binary invocation, (2) Run OpenGrep via container runtime, (3) Use Semgrep with compatibility flags.
- **Rationale:** Direct invocation keeps the worker simple for now while allowing config-driven rule selection; stub SARIF keeps the workflow moving in dev.
- **Consequences:** Requires `opengrep.bin` and `opengrep.config` to be configured in real deployments; containerization is still needed for production isolation.
- **Date:** 2026-01-25

## ADR-0012: Suppression matching precedence
- **Context:** The filter worker must decide how to match suppression entries to SARIF results and handle expiry/invalid rules.
- **Decision:** Match suppressions by SARIF fingerprint first, with a fallback to ruleId+line; treat expired suppressions as excluded and invalid timestamps as non-applicable.
- **Options considered:** (1) Fingerprint-only matching, (2) Fingerprint then region-based fallback, (3) Custom hash of message + rule + line.
- **Rationale:** Fingerprints are the most stable identifier; a minimal fallback keeps compatibility while avoiding over-suppression.
- **Consequences:** Some suppressions may not match if fingerprints are absent; future revisions can expand matching heuristics and report drift counts.
- **Date:** 2026-01-25

## ADR-0014: Verdict gating policy via environment-configured thresholds
- **Context:** The scan pipeline needs a deterministic PASS/FAIL verdict based on final SARIF findings, but no per-run policy object exists yet.
- **Decision:** Implement gating in the light worker using environment-configured thresholds (`gate.policy.fail-on-any`, `gate.policy.max-high`, `gate.policy.max-medium`, `gate.policy.max-low`) and return the verdict in the `ScanStatus` message while keeping state as `COMPLETED`.
- **Options considered:** (1) Fail on any finding with no configuration, (2) Add `gatePolicyRef` to the API request and fetch policy per run, (3) External policy service with dynamic thresholds.
- **Rationale:** Configured thresholds keep the pipeline deterministic without expanding the API surface; returning `COMPLETED` avoids conflating a failing gate with an operational failure.
- **Consequences:** Gating is global per environment until a per-run policy reference is added; policy changes require configuration updates.
- **Date:** 2026-01-25

## ADR-0016: Worker deployments and resource sizing defaults
- **Context:** The platform needs Kubernetes manifests with sensible defaults for each worker queue while keeping the heavy OpenGrep execution scalable.
- **Decision:** Deploy each worker type as a dedicated Deployment with baseline CPU/memory requests, use a shared artifacts PVC, and attach an HPA to the OpenGrep worker based on CPU utilization.
- **Options considered:** (1) Single worker Deployment for all queues, (2) Separate Deployments per queue without autoscaling, (3) Separate Deployments with HPA for OpenGrep only.
- **Rationale:** Dedicated Deployments isolate failure modes and allow tuning per queue; OpenGrep is the main scaling hotspot so it benefits from HPA first.
- **Consequences:** Resource defaults must be tuned per cluster; HPA requires metrics-server to be installed.
- **Date:** 2026-01-25

## ADR-0019: Triage service with CEL policy and JWT-secured review
- **Context:** Low-confidence false positives must be persisted for human triage, classified consistently, and pushed back to repos as signed suppressions.
- **Decision:** Add a `triage-service` (Quarkus + Postgres) that evaluates a repo-managed CEL policy from the default branch using only `cweId` and `confidencePercent`. Persist only "Potential False Positive" findings for review. Secure triage APIs with JWT role claim `secuirty:vuln_assessor:triager` and create suppression PRs via Git-over-HTTPS.
- **Options considered:** (1) Hardcoded thresholds in the filter worker, (2) Central policy engine (OPA) with remote policy storage, (3) Repo-managed CEL policy with local evaluation.
- **Rationale:** CEL keeps policy lightweight and repo-owned while enabling consistent classification; the triage service centralizes persistence, audit, and PR automation without expanding the workflow engine.
- **Consequences:** The triage service requires Postgres and JWT configuration; policy changes land via default-branch updates; PR URL generation may need template config for each SCM.
- **Date:** 2026-01-25
