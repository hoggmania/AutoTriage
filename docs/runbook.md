# Runbook

## Local Run
1. Install JDK 17+ and Maven.
2. Populate environment variables for Temporal (e.g., `TEMPORAL_TARGET`, `TEMPORAL_NAMESPACE`, `TEMPORAL_TLS_ENABLED=false`).
3. From the repo root, run `mvn -pl scan-api quarkus:dev` to boot the REST service.
4. Start Temporal developer server (Docker Compose or standalone) and configure the worker modules: `mvn -pl scan-worker-workflow quarkus:dev` plus the light/filter/opengrep workers.
5. If you are using the Temporal dev server, set `TEMPORAL_TARGET=localhost:7233` and `TEMPORAL_TLS_ENABLED=false`.
6. Start the suppression mock if you want uploader integration testing: `mvn -pl suppression-service-mock quarkus:dev` (defaults to port 8090).
7. Use cURL to POST `/scans` and watch Temporal Web UI for workflow progress; `/scans/{runId}` returns structured status.

## Kubernetes Run
1. Build containers with `docker build` using the Dockerfiles in `docker/` and push them to a registry.
2. Deploy Temporal (with TLS certs mounted via Secrets) and apply the manifests under `k8s/` which wire config as env vars.
3. Ensure TLS cert volumes are mounted and the Temporal target URL is available via ConfigMap.
4. Use Kubernetes Jobs or Deployments to schedule the worker replicas per queue: workflow, light, filter, opengrep.
5. Monitor the `/health` endpoints and Temporal UI; rotate certificates by replacing the Secrets and restarting pods as needed.
