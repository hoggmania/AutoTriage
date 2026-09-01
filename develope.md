# AutoTriage Development Guide

This page contains the engineering information needed to build, configure, and run AutoTriage locally.

[Return to the AutoTriage overview](README.md)

## Repository Structure

- `scan-common`: shared models and Temporal interfaces.
- `scan-api`: REST entry point (`/scans`, `/health`).
- `scan-worker-workflow`: orchestrates the full scan workflow.
- `scan-worker-light`: lightweight activities (repository, suppression, upload, and verdict).
- `scan-worker-filter`: SARIF filtering and optional ZeroFalse evaluation.
- `scan-worker-opengrep`: OpenGrep execution worker.
- `triage-service`: triage APIs, UI, CEL policy evaluation, audit trail, and suppression PR automation.
- `suppression-service-mock`: mock ingest service for local and integration flows.
- `docs/`: architecture, runbook, and implementation notes.
- `k8s/` and `docker/`: deployment artifacts.

## Local Development

### Prerequisites

- JDK 17
- Maven 3.9 or newer
- Temporal (development server or cluster)
- Git CLI
- Optional: Postgres for the triage service, OpenGrep binary/configuration, and LLM provider configuration

### Startup

1. Start Temporal. For the development server, set `TEMPORAL_TARGET=localhost:7233` and `TEMPORAL_TLS_ENABLED=false`.
2. Start the services and workers from the repository root:
   - `mvn -pl scan-api quarkus:dev`
   - `mvn -pl scan-worker-workflow quarkus:dev`
   - `mvn -pl scan-worker-light quarkus:dev`
   - `mvn -pl scan-worker-filter quarkus:dev`
   - `mvn -pl scan-worker-opengrep quarkus:dev`
3. For the optional local triage path:
   - Start Postgres.
   - Run `mvn -pl triage-service quarkus:dev`.
   - Set the JWT verification and database configuration.
4. For the optional suppression-ingest mock, run `mvn -pl suppression-service-mock quarkus:dev`.
5. Submit scans to `scan-api` and monitor `/scans/{runId}` and the Temporal UI.

### Verification

Run the complete Java 17 verification suite from the repository root:

```bash
mvn -B verify
```

### Containerized Local Run

- PowerShell: `scripts/podman-compose.ps1 up`
- Bash: `scripts/podman-compose.sh up`

## Key Configuration

### Common

- `TEMPORAL_TARGET`
- `TEMPORAL_NAMESPACE`
- `TEMPORAL_TLS_ENABLED`
- `ARTIFACTS_DIR`
- `GIT_CLONE_TOKEN`

### OpenGrep

- `OPENGREP_BIN`
- `OPENGREP_CONFIG`

### Filter and ZeroFalse

- `ZEROFALSE_ENABLED`
- `ZEROFALSE_MAX_FINDINGS`
- `ZEROFALSE_MAX_TRACE_STEPS`
- `ZEROFALSE_CONTEXT_LINES_BEFORE`
- `ZEROFALSE_CONTEXT_LINES_AFTER`
- `ZEROFALSE_PROMPTS_VARIANT`

### Triage

- `TRIAGE_SERVICE_URL`
- `TRIAGE_POLICY_PATH`
- `TRIAGE_POLICY_CACHE_MINUTES`
- `TRIAGE_PR_URL_TEMPLATE`
- The JWT role claim must include `security:vuln_assessor:triager`.

## Engineering Documentation

- [Architecture](docs/architecture.md)
- [Runbook](docs/runbook.md)
- [Triage plan](docs/triage-plan.md)
- [Decision log](docs/decisions/decision-log.md)
