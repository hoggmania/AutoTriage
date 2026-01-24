# Architecture Overview

AutoTriage is a Temporal-driven OpenGrep scanning platform structured as a Maven multi-module repository. The parent module coordinates the following actionable submodules:

- `scan-common`: shared DTOs, Temporal workflow/activities interfaces, and serialization helpers.
- `scan-api`: Quarkus HTTP entry point that validates ASTCLI requests, talks to Temporal via `scan-workflows` queue, and exposes `/scans` REST bindings plus `/health`.
- `scan-worker-workflow`: Temporal worker that hosts `OpenGrepPRScanWorkflowImpl` and orchestrates the activity graph on the `scan-workflows` task queue.
- `scan-worker-light`: light-weight activities (repo resolve, suppression fetch/verify, upload, verdict) which run against `scan-light` queue with higher retry counts.
- `scan-worker-filter`: SARIF suppression activity that filters heavy OpenGrep output; ticketed on `scan-filter` queue.
- `scan-worker-opengrep`: heavy OpenGrep execution container running on `scan-opengrep` queue with heartbeat-enabled, short retry policies.

Temporal namespace `scan-platform` hosts all workflows. The API and workers ship structured logging enriched with `runId`/`repo`/`prNumber`/`sha`. Configuration surfaces via environment variables so Kubernetes manifests can mount TLS certs and queue names without code changes.

Health endpoints run in each Quarkus service to satisfy the operational requirement, and Dockerfiles live in `docker/` for each runnable module. A GitHub Actions workflow enforces formatting + unit tests as the CI gate.
