# Runbook

## Local Run
1. Install JDK 17+ and Maven.
2. Populate environment variables for Temporal (e.g., `TEMPORAL_TARGET`, `TEMPORAL_NAMESPACE`, `TEMPORAL_TLS_ENABLED=false`).
3. From the repo root, run `mvn -pl scan-api quarkus:dev` to boot the REST service.
4. Start Temporal developer server (Docker Compose or standalone) and configure the worker modules: `mvn -pl scan-worker-workflow quarkus:dev` plus the light/filter/opengrep workers.
5. If you are using the Temporal dev server, set `TEMPORAL_TARGET=localhost:7233` and `TEMPORAL_TLS_ENABLED=false`.
6. Start the suppression mock if you want uploader integration testing: `mvn -pl suppression-service-mock quarkus:dev` (defaults to port 8090) and set `SUPPRESSION_SERVICE_URL=http://localhost:8090`.
7. For repo cloning in the light worker, ensure `git` is installed and set `GIT_CLONE_TOKEN` if required for private repositories.
8. Optional: set `ARTIFACTS_DIR=artifacts` to control where the source tarball and suppression bundle are written.
9. Configure the OpenGrep worker with `OPENGREP_BIN=opengrep` and `OPENGREP_CONFIG=/path/to/rules.yml` to generate SARIF (it writes stub SARIF if unset).
10. Optional: enable ZeroFalse LLM suppressions in the filter worker with `ZEROFALSE_ENABLED=true` and configure a Quarkus LangChain4j provider (for example `quarkus.langchain4j.openai.*`).
11. Optional tuning for ZeroFalse: `ZEROFALSE_MAX_FINDINGS`, `ZEROFALSE_MAX_TRACE_STEPS`, `ZEROFALSE_CONTEXT_LINES_BEFORE`, `ZEROFALSE_CONTEXT_LINES_AFTER`, `ZEROFALSE_PROMPTS_VARIANT`.
12. Use cURL to POST `/scans` and watch Temporal Web UI for workflow progress; `/scans/{runId}` returns structured status.

## Podman Compose Run
1. Start a Temporal dev server on the host (or set `TEMPORAL_TARGET` to your cluster).
2. From the repo root, run `scripts/podman-compose.ps1 up` (PowerShell) or `scripts/podman-compose.sh up` (bash) to build and start containers.
3. `scan-api` listens on port 8080 and the suppression mock on 8090; `ARTIFACTS_DIR` is shared across workers via a podman volume.
4. To stop everything, run `scripts/podman-compose.ps1 down` or `scripts/podman-compose.sh down`.
5. Optional overrides: set `GIT_CLONE_TOKEN` for private repos and `OPENGREP_BIN`/`OPENGREP_CONFIG` for real SARIF output.

## Kubernetes Run
1. Build containers with `docker build` using the Dockerfiles in `docker/` and push them to a registry.
2. Deploy Temporal (with TLS certs mounted via Secrets) and apply the manifests under `k8s/` which wire config as env vars.
3. Ensure TLS cert volumes are mounted and the Temporal target URL is available via ConfigMap.
4. Use Kubernetes Jobs or Deployments to schedule the worker replicas per queue: workflow, light, filter, opengrep.
5. Monitor the `/health` endpoints and Temporal UI; rotate certificates by replacing the Secrets and restarting pods as needed.
