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
