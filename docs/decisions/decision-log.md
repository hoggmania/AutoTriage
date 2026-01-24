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
