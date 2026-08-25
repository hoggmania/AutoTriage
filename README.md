# AutoTriage

LLM or Agentic Security testing combines context with security issues; this has been highly effective with Models such as Mythos. However, the cost security testing with such LLM's within a CI/Pipeline, comes with several issues: -
- costly to excute LLM scanning for every build, especially in high build cadence systems
- delay/blocks build infrastructure (even when LLM execution is separate)
- llm has could access to build environment, dangerous actions maybe permitted
- suitable in-line triage process not part of LLM process (what to allow or not)
- model/RAG training/context specific to consumer should not be part of target source

AutoTriage is a Proof-of-Concept on separation of expensive LLM security testing away from the CI, allowing for blending with more CI friendly tooling; in this case using OpenGrep SARIF results.

The platform runs scans for repositories at defind periods (PRs/schedule), applies suppressions, and then uses LLM-assisted false-positive filtering; low-confidence findings are routed into a reviewer workflow that can create suppression pull requests. CI/pipelines can still run the SAST tooling, with signed supression files, and trust the results for gating purposes. 



## Project Aims

- Automate OpenGrep scanning as a repeatable workflow.
- Reduce noisy findings by applying suppression bundles and optional ZeroFalse AI evaluation.
- Keep human review focused on ambiguous findings (Potential False Positives).
- Enforce policy-driven triage classification from repo-managed CEL rules.
- Provide auditable triage actions and suppression PR automation.

## High-Level Flow

1. A client submits `POST /scans` to `scan-api`.
2. `scan-api` starts `OpenGrepPRScanWorkflow` in Temporal.
3. Workflow activities run in order:
   - resolve repository source
   - fetch and verify suppression bundle
   - run OpenGrep
   - filter/apply suppressions (with optional ZeroFalse)
   - upload results
   - compute final verdict
4. Filter worker can submit low-confidence candidates to `triage-service`.
5. `triage-service` loads CEL policy from the repo default branch (default `.autotriage/policy.cel`) and classifies candidates.
6. Only Potential False Positives are persisted for review.
7. Security reviewers claim/approve/deny findings in triage APIs/UI.
8. On approval, triage automation creates suppression updates in the repo and pushes a branch for PR creation.

## Triage Classification Model

Policy input is intentionally narrow:

- `cweId`
- `confidencePercent`

Default policy bands described in project docs:

- `0-30`: True Positive
- `30-60`: Potential False Positive
- `61-100`: False Positive

## Repository Structure

- `scan-common`: shared models and Temporal interfaces.
- `scan-api`: REST entry point (`/scans`, `/health`).
- `scan-worker-workflow`: orchestrates the full scan workflow.
- `scan-worker-light`: lightweight activities (repo/suppression/upload/verdict).
- `scan-worker-filter`: SARIF filtering and optional ZeroFalse evaluation.
- `scan-worker-opengrep`: OpenGrep execution worker.
- `triage-service`: triage APIs, UI, CEL policy eval, audit trail, suppression PR automation.
- `suppression-service-mock`: mock ingest service for local/integration flows.
- `docs/`: architecture, runbook, and implementation notes.
- `k8s/` and `docker/`: deployment artifacts.

## Local Development

Prerequisites:

- JDK 17
- Maven 3.9 or newer
- Temporal (dev server or cluster)
- Git CLI
- Optional: Postgres (for triage service), OpenGrep binary/config, LLM provider config

Typical startup sequence:

1. Start Temporal (for dev server: `TEMPORAL_TARGET=localhost:7233`, `TEMPORAL_TLS_ENABLED=false`).
2. Start services/workers from repo root:
   - `mvn -pl scan-api quarkus:dev`
   - `mvn -pl scan-worker-workflow quarkus:dev`
   - `mvn -pl scan-worker-light quarkus:dev`
   - `mvn -pl scan-worker-filter quarkus:dev`
   - `mvn -pl scan-worker-opengrep quarkus:dev`
3. Optional local triage path:
   - start Postgres
   - `mvn -pl triage-service quarkus:dev`
   - set JWT verification config and DB env vars
4. Optional suppression ingest mock:
   - `mvn -pl suppression-service-mock quarkus:dev`
5. Submit scans to `scan-api` and monitor status via `/scans/{runId}` and Temporal UI.

Run the complete Java 17 verification suite from the repository root with:

```bash
mvn -B verify
```

For containerized local run, use:

- PowerShell: `scripts/podman-compose.ps1 up`
- Bash: `scripts/podman-compose.sh up`

## Key Configuration

Common:

- `TEMPORAL_TARGET`
- `TEMPORAL_NAMESPACE`
- `TEMPORAL_TLS_ENABLED`
- `ARTIFACTS_DIR`
- `GIT_CLONE_TOKEN`

OpenGrep:

- `OPENGREP_BIN`
- `OPENGREP_CONFIG`

Filter/ZeroFalse:

- `ZEROFALSE_ENABLED`
- `ZEROFALSE_MAX_FINDINGS`
- `ZEROFALSE_MAX_TRACE_STEPS`
- `ZEROFALSE_CONTEXT_LINES_BEFORE`
- `ZEROFALSE_CONTEXT_LINES_AFTER`
- `ZEROFALSE_PROMPTS_VARIANT`

Triage:

- `TRIAGE_SERVICE_URL`
- `TRIAGE_POLICY_PATH`
- `TRIAGE_POLICY_CACHE_MINUTES`
- `TRIAGE_PR_URL_TEMPLATE`
- JWT role claim must include `security:vuln_assessor:triager`

## Related Docs

- `docs/architecture.md`
- `docs/autotriage-vs-agentic-security-tools.md`
- `docs/runbook.md`
- `docs/triage-plan.md`
- `docs/decisions/decision-log.md`

## License

AutoTriage is licensed under the [MIT License](LICENSE).
