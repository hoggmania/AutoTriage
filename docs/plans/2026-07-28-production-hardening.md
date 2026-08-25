# AutoTriage Production Hardening Implementation Plan

> **For Hermes:** Use subagent-driven-development to implement this plan task-by-task, with specification and code-quality review before final verification.

**Goal:** Turn AutoTriage from a shared-filesystem proof of concept into a licensable, durable, isolated, identity-backed, Kubernetes-deployable security analysis platform.

**Architecture:** Standardize on Java 17 and Maven 3.9, S3-compatible content-addressed object storage, an engine/evidence SPI in `scan-common`, AWS KMS asymmetric signatures, GitHub App installation authentication plus GitHub REST branch/commit/pull-request APIs, and separate least-privilege Kubernetes worker classes. Temporal remains the durable orchestrator; workflow data carries immutable artifact references and evidence provenance rather than local paths.

**Tech Stack:** Java 17, Maven 3.9, Quarkus 3.15, Temporal Java SDK/testing, AWS SDK v2 (S3/KMS), GitHub REST API, Postgres 16, MinIO for local Kubernetes object storage, Kubernetes NetworkPolicy/RuntimeClass/securityContext.

---

## Delivery gates

1. `mvn -B verify` runs on Java 17 with zero skipped core triage tests.
2. No production worker emits or accepts `file://` artifact references.
3. Every stored artifact has a SHA-256 digest, size, media type, immutable object key, and producing run/engine provenance.
4. OpenGrep is invoked only through the engine SPI and sandbox runner.
5. Temporal tests prove task-queue separation, activity retry, replay/resume compatibility, and cancellation behavior.
6. GitHub branch/commit/PR lifecycle uses a GitHub App installation token and REST APIs; no fabricated compare URL is returned as a PR.
7. Suppression signatures use asymmetric KMS identities; HMAC is removed from production code and configuration.
8. `kubeconform -strict` passes for the complete Kubernetes install.

## Task 1: Add MIT licensing

**Files:**
- Create: `LICENSE`
- Modify: `pom.xml`, `README.md`, `.github/workflows/ci.yml`

**Steps:**
1. Add the canonical MIT license text with `Copyright (c) 2026 James Hogg`.
2. Add Maven `<licenses>` metadata and a README license section.
3. Add a CI assertion that `LICENSE` exists and contains the MIT grant.
4. Run `git diff --check`.

## Task 2: Align Java, Maven, CI, containers, and documentation

**Files:**
- Modify: `pom.xml`, all module `pom.xml` files as needed, `.github/workflows/ci.yml`, `README.md`, `docs/runbook.md`, `docs/architecture.md`, `docs/triage-plan.md`, `docker/*.Dockerfile`
- Modify tests: `triage-service/src/test/java/**`

**Steps:**
1. Make Java 17 the single advertised compiler/runtime/CI/container version and Maven 3.9 the minimum.
2. Remove JRE-range conditions that skip triage tests on the advertised runtime.
3. Replace relocated Quarkus REST artifact IDs and ensure JSON-capable services include `quarkus-rest-jackson`.
4. Run the full Java 17 reactor in a Temurin 17 Maven container; preserve failing evidence before fixes.
5. Fix test fixtures/configuration until all core triage tests execute and pass.
6. Remove `-DskipTests` from production Docker build stages; use `-DskipTests` only in a documented image-only CI stage after the verified reactor.

## Task 3: Define immutable artifact storage SPI and references

**Files:**
- Create: `scan-common/src/main/java/com/autotriage/common/artifact/ArtifactStore.java`
- Create: `scan-common/src/main/java/com/autotriage/common/artifact/ArtifactContent.java`
- Modify: `scan-common/.../model/ArtifactRef.java`
- Create module: `scan-artifact-s3/` with `S3ArtifactStore` and tests
- Modify: root `pom.xml`; light/filter/OpenGrep worker POMs and bootstraps

**Steps:**
1. Write failing contract tests for immutable put/get, SHA-256 validation, deduplication, size/media-type metadata, and tamper rejection.
2. Extend `ArtifactRef` with `sha256`, `sizeBytes`, `mediaType`, and immutable `objectKey`; reject `file` scheme for durable references.
3. Implement S3-compatible content-addressed storage at `sha256/<first-two>/<digest>` with conditional/idempotent writes and digest verification on reads.
4. Provide an in-memory implementation only for tests; production fails closed when S3 configuration is absent.
5. Add MinIO endpoint/path-style support for local and Kubernetes environments.

## Task 4: Replace worker-local artifacts with object storage

**Files:**
- Modify: `LightScanActivities.java`, `OpenGrepScanActivities.java`, `FilterScanActivities.java`, `ZeroFalseContextBuilder.java`
- Modify corresponding tests and worker configuration

**Steps:**
1. Write failing tests proving workers exchange object references rather than local URIs.
2. Materialize objects only into private temporary directories immediately before use; verify digest and size before parsing/extracting.
3. Upload source archives, suppression bundles, raw/final SARIF, and reports through `ArtifactStore` and return content-addressed refs.
4. Delete temporary materializations in `finally` blocks and remove shared `ARTIFACTS_DIR`/PVC assumptions.
5. Reject absolute/file SARIF locations and constrain all resolved source paths beneath the extracted source root.

## Task 5: Define engine and evidence SPI

**Files:**
- Create: `scan-common/src/main/java/com/autotriage/common/engine/{AnalysisEngine,EngineDescriptor,EngineRequest,EngineResult,EngineRegistry}.java`
- Create: `scan-common/src/main/java/com/autotriage/common/evidence/{Evidence,EvidenceLevel,EvidenceProvenance,EvidenceAssessment,EvidenceCalibrator}.java`
- Modify: `ScanActivities.java`, workflow contracts, OpenGrep worker implementation and tests

**Steps:**
1. Write SPI contract tests for stable engine identity/version, declared inputs/outputs, immutable evidence, and deterministic serialization.
2. Keep scanner execution (`AnalysisEngine`) separate from evidence calibration and policy classification.
3. Adapt OpenGrep as the first registered engine; the workflow selects it by stable engine ID rather than hard-coded method naming.
4. Reject unknown/unregistered engines before workflow execution.

## Task 6: Add calibrated evidence and provenance to triage

**Files:**
- Modify: `TriageCandidateRequest`, `ZeroFalseVerdict`, filter triage submission, CEL policy variables
- Create: `triage-service/src/main/resources/db/migration/V2__evidence_provenance.sql`
- Modify: `FindingEntity`, `FindingView`, `TriageCandidateService`, UI, API tests, policy tests, docs

**Steps:**
1. Write failing persistence/API tests for evidence level and full provenance.
2. Add evidence levels `INSUFFICIENT`, `LIMITED`, `MODERATE`, `STRONG` and calibration metadata (`calibratorId`, version/profile, raw score where available).
3. Persist engine ID/version, rule, model/provider, prompt/template version and hash, source/SARIF artifact hashes, evaluation time, and calibration identity.
4. Expose evidence/provenance to CEL as structured inputs; deprecate `confidencePercent` as policy input and retain it only as legacy raw telemetry during migration.
5. Default policy must fail safe when evidence/provenance is missing and must not turn a single generic percentage directly into suppression.

## Task 7: Sandbox every analysis engine

**Files:**
- Create: `scan-common/.../sandbox/{SandboxRunner,SandboxPolicy,SandboxResult}.java`
- Create/modify: OpenGrep sandbox adapter and tests
- Modify: `docker/scan-worker-opengrep.Dockerfile`, Kubernetes OpenGrep manifests

**Steps:**
1. Write tests for command construction, timeout, output cap, cancellation, environment allowlist, read-only input, and no-network policy.
2. Run OpenGrep through a fail-closed sandbox backend (bubblewrap locally/container, RuntimeClass isolation in Kubernetes); never invoke scanner binaries directly.
3. Reject symlink/hardlink/device/archive bombs, duplicate archive entries, path traversal, oversized files, excessive file counts, and decompression ratios before engine execution.
4. Validate and bound `runId`, refs, archive sizes, SARIF sizes, and process output.
5. Use non-root, read-only root filesystem, dropped capabilities, seccomp, bounded `emptyDir`, no token mount, and deny-all egress for scanner pods.

## Task 8: Add Temporal end-to-end durability tests

**Files:**
- Modify: `scan-worker-workflow/pom.xml`
- Create: `scan-worker-workflow/src/test/java/com/autotriage/worker/workflow/OpenGrepPRScanWorkflowE2ETest.java`
- Modify workflow cancellation/activity options where tests expose gaps

**Steps:**
1. Use Temporal `TestWorkflowEnvironment` with separate workflow/light/filter/OpenGrep workers and queue-specific fake activities.
2. Prove transient activity failure retries and eventually completes with the expected attempt count.
3. Prove each activity ran on its assigned queue/worker identity.
4. Export workflow history and replay it to prove deterministic resume/replay compatibility.
5. Block a heartbeat-enabled activity, send cancellation, assert cancellation propagates and no later activities execute.
6. Run focused test and full reactor.

## Task 9: Replace Git-over-HTTPS PR automation with GitHub App/API lifecycle

**Files:**
- Create: `triage-service/.../github/{GitHubAppAuthenticator,GitHubApiClient,GitHubRepositoryRef,GitHubPullRequest}.java`
- Refactor: `SuppressionUpdateService.java`; retire `GitCredentialsProvider` and JGit branch/push lifecycle
- Add WireMock/mock-server tests and configuration docs

**Steps:**
1. Generate a short-lived GitHub App JWT from App ID/private key and exchange it for an installation token.
2. Resolve installation ID and default branch through GitHub APIs.
3. Create branch refs, read/update suppression files through Contents/Git Data APIs, create commits, and create/reuse a pull request through `/pulls`.
4. Persist real PR number, node/HTML URL, head SHA, base branch, and API state; make retries idempotent.
5. Never place tokens in clone URLs, logs, database fields, or returned errors.

## Task 10: Replace HMAC with AWS KMS asymmetric identities

**Files:**
- Create: `scan-common/.../identity/{SignatureEnvelope,SigningIdentity,SignatureVerifier}.java`
- Create module/shared implementation: AWS KMS signer/verifier with tests
- Refactor: light verifier and triage suppression signer
- Remove HMAC/test-signature production settings from code, compose, manifests, and docs

**Steps:**
1. Define a versioned signature envelope containing key ARN, algorithm, payload SHA-256, signature, signed-at time, and identity metadata.
2. Sign the canonical suppression payload digest with asymmetric AWS KMS `RSASSA_PSS_SHA_256`.
3. Verify through KMS or a pinned public-key/certificate chain linked to the allowed KMS key ARN; enforce key allowlists and algorithm binding.
4. Use workload identity/default AWS credential chain; do not store signing secrets.
5. Keep a fake signer/verifier only in test scope.

## Task 11: Complete Kubernetes deployment

**Files:**
- Add `k8s/base/kustomization.yaml` and manifests for namespace, triage service, Postgres StatefulSet/service/PVC, MinIO StatefulSet/service/PVC/bucket job, per-workload ServiceAccounts, Secrets examples, NetworkPolicies, PDBs, RuntimeClass and worker deployments
- Add `k8s/overlays/dev` and `k8s/overlays/production`
- Remove shared artifact PVC and secret-containing tracked manifests
- Modify `docs/runbook.md`

**Steps:**
1. Build a self-contained dev overlay with Postgres and MinIO; production overlay references managed DB/S3/KMS/GitHub App secrets.
2. Assign isolated worker classes, service accounts, resource limits, scheduling constraints, and network policies.
3. Add readiness/liveness/startup probes and migration-safe deployment behavior.
4. Validate all rendered manifests with `kustomize build` and `kubeconform -strict`.
5. Document secret creation, workload identity, bucket initialization, DB migration, rollout, smoke test, backup, and recovery.

## Task 12: Final integration and security review

**Files:** all changed files

**Steps:**
1. Run focused tests for each module, then `mvn -B verify` on Java 17.
2. Run `git diff --check`, dependency/security checks available in CI, and scan for `file://`, HMAC secrets, token URL injection, and skipped core tests.
3. Build all container images and run a local object-store/Temporal/Postgres smoke flow where dependencies are available.
4. Render and validate both Kubernetes overlays.
5. Perform separate spec-compliance and code-quality/security reviews; fix all critical and important findings.

## Scope notes

- External GitHub/KMS calls are integration-tested against HTTP/service fakes locally; live smoke tests require user-provided GitHub App and cloud workload identity and must not be faked.
- The repository has only one scanner today (OpenGrep). “Every analysis engine” therefore means OpenGrep now and an SPI rule that makes sandboxing mandatory for future engine registrations.
- Runtime migration is intentionally Java 17 because the POM, CI, and container images already target 17; documentation claiming Java 25 is stale.
