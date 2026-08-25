# The scanner is not the security program

## Comparing AutoTriage with VulnHunter, RAPTOR and Visa's Vulnerability Agentic Harness

Agentic security tools are getting good at finding vulnerabilities. Some trace attacker-controlled data through a codebase. Some build exploit hypotheses, generate proof-of-concept payloads or propose patches. A few will edit the target repository for you.

That progress creates a second problem: who decides which engine runs, which evidence is trusted, what an agent may change, when a human must intervene, and how the decision survives the next scan?

That is the useful way to compare AutoTriage with Capital One's VulnHunter, RAPTOR and Visa's Vulnerability Agentic Harness (VVAH). They overlap, but they are not the same kind of system.

VulnHunter, RAPTOR and VVAH are analysis systems.[2][4][7]
AutoTriage is trying to become the control plane around analysis.[1]

This comparison is based on source inspection, not product-page claims. It uses the following point-in-time revisions:

- AutoTriage `09bfe3d` (25 August 2026).[1]
- VulnHunter `8c2e20a` (23 July 2026).[2][3]
- RAPTOR `e6c3f25` (24 July 2026).[4][5][6]
- VVAH `d91b28d` (3 July 2026).[7][8][9]

## Four systems, four centres of gravity

| System | Centre of gravity | Strongest capability | Main operational gap |
|---|---|---|---|
| AutoTriage | Durable scan governance | Policy, review, evidence provenance, suppressions and workflow state | Analysis breadth is still narrow |
| VulnHunter | Attacker-first source investigation | Falsification, exploit reasoning and test-driven fixes | Most governance lives in prompts and local operator flow |
| RAPTOR | Broad security research workbench | Static, binary, SCA, runtime and exploitability analysis | No central multi-user decision and suppression service |
| Visa VVAH | Structured agentic SAST lifecycle | Threat modelling through remediation review | Local pipeline, permissive operational assumptions and no executable fix proof |

If the question is "which tool can perform the deepest vulnerability analysis?", AutoTriage is not the winner. RAPTOR has far more analysis machinery. VulnHunter has a stronger hunt-fix-verify method. VVAH has a much broader agentic source-analysis pipeline.

If the question is "how do we run different engines across many repositories without surrendering policy, review and auditability to whichever agent happens to be executing?", the comparison changes.

## VulnHunter: prove it, fix it, verify it

VulnHunter is built around an attacker-first workflow. It inventories inputs, traces them toward dangerous operations, tries to disprove weak candidates, produces exploit evidence, searches for sibling instances and proposes fixes. Its remediation workflow is deliberately test driven: reproduce the issue, add a failing security test, apply the change, prove the test turns green, run regression tests and prepare a reviewable delivery.[2][3]

That is a better vulnerability investigation method than matching a static-analysis rule and asking one model whether the result looks suspicious.[3]

VulnHunter also separates the fixer from the verifier. The verifier applies explicit gates around reachability, sink mitigation, vulnerability-class elimination and completeness. Failed verification can feed back into the issue lifecycle. That separation is healthy because an agent should not grade its own patch.

The boundary is still softer than it first appears.[2][3]
Normal hunting is read-only and Bash requires an explicit opt-in, but much of the workflow's correctness depends on large prompt contracts.[2]
Its verifier's read-only behaviour is not equivalent to an immutable filesystem boundary.
The shipped sandbox configuration is optional rather than the default.[2]
The project describes CI and fleet-worker use, but the inspected revision did not ship a complete queue, service deployment or central reviewer plane.[2][3]

VulnHunter therefore makes sense as a high-value investigation engine behind AutoTriage. AutoTriage can decide when a finding deserves an expensive hunt, provide the immutable source revision and evidence envelope, keep the run within a budget, and require approval before exploit execution or source mutation.

## RAPTOR: the analysis workbench AutoTriage should not rebuild

RAPTOR is the broadest system in this comparison. It combines Semgrep, CodeQL, Coccinelle, dependency analysis, SBOM generation, binary tooling, fuzzing, runtime instrumentation, SMT reasoning, exploit generation and LLM-assisted analysis. Its validation workflow mixes mechanical inventory with exploit hypotheses, source checks, binary feasibility, runtime evidence and final rulings.[4][5]

The important word is *workbench*. RAPTOR gives a security researcher many ways to interrogate a target.[4][5] AutoTriage currently gives an organisation one primary scanner, OpenGrep, plus bounded LLM adjudication and governed triage.[1]

Trying to close that capability gap by copying RAPTOR would be a bad use of the project. RAPTOR has years of analysis surface, test fixtures and specialist code. Reimplementing it would turn AutoTriage into a weaker scanner while starving the control-plane work that makes AutoTriage distinct.

RAPTOR is also the strongest reference here for hostile-repository handling. Its documented sandbox uses namespaces, Landlock, seccomp, constrained mounts, resource limits, fake home directories and blocked or allowlisted network access. It checks for hostile Claude configuration, module shadowing and prompt-envelope manipulation. The maintainers still identify natural-language prompt injection and over-capable agents as open risks, which is more credible than pretending the problem has been solved.[6]

RAPTOR's governance remains local and operator-centred.[4][6]
It has project history, local artefacts and suppression support, but not the equivalent of an authenticated reviewer queue, organisation-owned policy evaluation, signed suppression lifecycle or durable distributed workflow state.[4][6]

The sensible integration sequence is:

1. ingest RAPTOR's deterministic and SCA outputs;
2. invoke agentic analysis only for selected findings or repositories;
3. reserve PoC execution and runtime validation for isolated workers with explicit approval.

AutoTriage should own the policy and evidence contract. RAPTOR should own the specialist analysis.

## VVAH: a complete agentic pipeline with dangerous defaults

VVAH is the closest conceptual competitor because it covers the whole source-analysis path. Its eleven stages map the attack surface, build a threat model, decompose the repository into risk-focused work, run specialist analysis, prefilter weak findings, perform adversarial verification, deduplicate, construct exploit chains, emit SARIF, remediate and then review the proposed fix.[7]

It also has practical controls: Pydantic contracts, SQLite checkpoints, batch processing, per-stage model routing, token budgets, repository-confined read tools and redaction.[7][8]
The SDK and OpenAI routes restrict agents to Read, Glob and Grep during analysis.[7][8]

There are two caveats that matter in an enterprise pipeline.

First, a plain VVAH scan continues into remediation and edits the target working tree by default. Detection-only operation requires `--stop-after s9`. Report-only remediation still exposes edit tools and relies on the prompt to avoid using them. The default CLI route keeps Bash out of its allowlist, but that protection depends on configuration rather than an external immutable boundary.[7][9]

Second, VVAH's validation is adversarial model review, not executable proof.[7][8]
It does not compile the patched code, run the target's test suite or replay an exploit.[7][8]
Its own documentation tells users to review and build the fixes before relying on them.[7][8]

That does not make VVAH weak. It makes its evidence level clear: a model-reviewed patch is not the same thing as a build-tested patch, and neither is the same thing as a reproduced vulnerability.

AutoTriage should run VVAH in detection-only mode for normal ingestion. Remediation belongs in a disposable worktree or branch after policy and human approval. AutoTriage can then record whether a fix was merely proposed, reviewed by another model, compiled, regression-tested or proven against the original exploit.

## Where AutoTriage is genuinely different

AutoTriage's useful features sit above the scanner:

- Temporal keeps workflow state, retries and cancellation separate from any one worker process.
- Analysis, filtering and light orchestration run on separate task queues.
- The engine SPI avoids hard-coding every future scanner into the workflow.
- Content-addressed artifact references and provenance give evidence an immutable identity.
- Evidence levels distinguish weak model confidence from stronger, attributable support.
- Repository-owned CEL policy decides which findings need human review.
- Reviewers claim, approve or deny ambiguous findings through an authenticated service.
- Suppressions are versioned in the repository, expire and carry an integrity envelope.
- The audit trail records the finding, evidence, decision and resulting action.[1]

That architecture answers questions an analysis engine should not answer for itself:

- May this engine send source to an external model?
- Is network access allowed for this run?
- Is the evidence strong enough to block a build?
- Has the same finding already been reviewed?
- Who approved the suppression?
- Which policy version made the decision?
- Which model, prompt and source artifact produced the recommendation?
- May an agent execute a PoC or edit source code?

The distinction matters because agent output is not self-authenticating. A JSON object that says `confidence: 0.97` does not tell us whether the model saw the full data flow, whether the repository manipulated the prompt, whether the result was reproduced, or whether the patch passed a single test.

AutoTriage's evidence contract should preserve those differences rather than flatten them into one confidence percentage.

## Where AutoTriage still loses

A control plane with one scanner is not much of a control plane.

OpenGrep gives AutoTriage repeatable SAST coverage, but it cannot match RAPTOR's source, binary, SCA and runtime breadth.[1][4]
Bounded LLM adjudication can reduce false positives among findings OpenGrep already found; it cannot discover business-logic vulnerabilities that the underlying rule set missed.[1]
VulnHunter and VVAH are better at semantic exploration, threat-led analysis and candidate remediation.[2][3][7]

The project also has unfinished operational work. The codebase now contains S3 artifact storage, an engine/evidence SPI, asymmetric KMS identity implementations, Temporal end-to-end tests and broader Kubernetes deployment assets. Some lifecycle edges still need tightening, particularly the full GitHub App pull-request flow, production wiring of signing identities and proof that every analysis engine runs inside a fail-closed hostile-input sandbox.[1]

That is the right backlog. Adding another clever prompt before those boundaries are finished would create a better demo and a worse security platform.

## The security boundary is the product

These tools process attacker-influenced repositories. That changes the threat model.

A malicious repository can contain prompt injection, pathological archives, symlink traps, giant files, hostile build scripts, poisoned local agent configuration and source designed to exfiltrate secrets through model calls. Giving an agent Bash, credentials and unrestricted network access turns source review into remote code execution with extra steps.

RAPTOR currently offers the strongest sandbox reference in this group.[6] VVAH provides useful path jails and output controls but explicitly recommends host-level isolation for less-trusted targets.[8] VulnHunter contains careful token and Git handling, yet its sandbox is not the default.[2]

AutoTriage needs a non-negotiable execution contract for every engine:

- immutable, digest-verified input;
- disposable workspace;
- read-only source mount;
- non-root process;
- no inherited credentials;
- denied network unless a reviewed policy grants specific destinations;
- bounded CPU, memory, disk, files and output;
- archive and path-traversal checks before execution;
- explicit approval before target-code execution or mutation;
- provenance showing which boundary actually ran.

The engine adapter should not be able to opt out. If the control plane cannot prove the sandbox policy was enforced, it should downgrade or reject the evidence.

## A better combined architecture

The four projects fit together better than they compete.

### Routine pull requests

Run OpenGrep or another deterministic engine.[1] Apply only verified, unexpired suppressions.[1] Send new or ambiguous findings through bounded model adjudication.[1] Preserve anything that exceeds the budget rather than treating it as safe.

### Higher-risk repositories

Add RAPTOR deterministic analysis and SCA. Trigger VVAH detection-only analysis at release boundaries or after material architecture changes. Use a common evidence envelope so findings can be deduplicated without losing provenance.

### Difficult findings

Escalate selected cases to VulnHunter for attacker-first tracing and falsification. Use RAPTOR validation when binary, SMT, runtime or exploit evidence can settle the question.

### Remediation

Require a human decision before an agent writes source. Run the fixer in a disposable branch or worktree. Treat generated patches as proposals until the project builds, tests pass and any exploit reproduction no longer succeeds. Keep the merge decision outside the analysis engine.

This gives each system the job it is best equipped to do:

- scanners discover candidates;
- specialist engines deepen the evidence;
- models interpret bounded context;
- deterministic controls enforce policy;
- humans own consequential ambiguity;
- AutoTriage records and orchestrates the lifecycle.

## The product decision

AutoTriage should not market itself as an AI vulnerability scanner. That invites a feature comparison it cannot win and should not try to win.

A better description is:

> AutoTriage is the control plane that turns findings from scanners and security agents into governed, reviewable and auditable decisions.

The next milestone is not "more agents". It is proving that several very different engines can plug into the same evidence, policy, isolation, review and suppression lifecycle without gaining authority they were never meant to have.

That is harder than wrapping a model around a repository. It is also the part most security teams will eventually need.

## Sources

[1] https://github.com/hoggmania/AutoTriage/tree/09bfe3dd945347aaa7a32009388512ad84c7c26c
[2] https://raw.githubusercontent.com/capitalone/vulnhunter/8c2e20a3dd2d1f529c51811fe3d272f83cb6a254/README.md
[3] https://raw.githubusercontent.com/capitalone/vulnhunter/8c2e20a3dd2d1f529c51811fe3d272f83cb6a254/vulnhunt/SKILL.md
[4] https://raw.githubusercontent.com/gadievron/raptor/e6c3f253ac21e36df772c13f28c91009db8286c1/README.md
[5] https://raw.githubusercontent.com/gadievron/raptor/e6c3f253ac21e36df772c13f28c91009db8286c1/docs/validation.md
[6] https://raw.githubusercontent.com/gadievron/raptor/e6c3f253ac21e36df772c13f28c91009db8286c1/docs/security.md
[7] https://raw.githubusercontent.com/visa/visa-vulnerability-agentic-harness/d91b28d3af7ee46f9cc1827a9377cbdedd1f6b7e/README.md
[8] https://raw.githubusercontent.com/visa/visa-vulnerability-agentic-harness/d91b28d3af7ee46f9cc1827a9377cbdedd1f6b7e/docs/security.md
[9] https://raw.githubusercontent.com/visa/visa-vulnerability-agentic-harness/d91b28d3af7ee46f9cc1827a9377cbdedd1f6b7e/docs/remediation.md
