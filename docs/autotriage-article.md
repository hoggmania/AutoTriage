# Stop sending the entire codebase to an LLM

## Use deterministic SAST for broad, repeatable detection. Spend LLM tokens only on the findings that need semantic judgment.

Security teams should stop treating a whole-repository LLM scan as a routine CI job. The more sustainable design is a hybrid: run deterministic static analysis on every pull request, apply governed suppressions, and send only new or ambiguous findings to an LLM. Reserve full agentic audits for major releases and high-risk systems.

That approach cuts routine LLM spend by roughly 95% to more than 99% while keeping the model where it adds the most value: interpreting context, data flow, sanitization and exploitability.

## Whole-repository LLM scanning is the wrong default

A frontier model can reason about code in ways traditional rules cannot. That does not mean it should reread 100,000 lines every time somebody changes a controller.

The cost is no longer hypothetical. The RealVuln benchmark normalized general-purpose LLM security scanning to 100,000 lines of code. Reported costs ranged from $8 for MiniMax M2.7 and $11 for Kimi K2.5 to $83 for Claude Sonnet 4.6, $123 for Claude Opus 4.6 and $136 for Gemini 3.1 Pro.[1]

One scan may be affordable. CI turns one scan into a recurring bill.

| Full scans per month | Benchmark cost range |
|---:|---:|
| 4 | $32–$544 |
| 30 | $240–$4,080 |
| 100 | $800–$13,600 |

Those figures cover direct scan cost. They do not include analyst time spent reviewing noisy output, retries after failed runs, or the delay introduced when an agent has to explore a large repository.

The benchmark also found that cost did not reliably buy better results. Claude Opus 4.6 completed only 19 of 26 repositories, while cheaper models sometimes produced better cost-adjusted performance.[1] Paying for a larger context window and a more expensive model does not guarantee a complete or useful scan.

## The LLM should judge evidence, not rediscover the repository

AutoTriage changes the unit of work.

OpenGrep performs broad, deterministic discovery and emits SARIF. Existing signed suppressions remove findings the organisation has already reviewed. The LLM then receives a bounded evidence package for each remaining candidate: the weakness class, selected source locations, a short data-flow trace and a few lines of code around each step.

The current AutoTriage defaults cap LLM evaluation at 25 findings, 20 trace steps per finding, and two lines of source before and after each location. The model returns a small JSON verdict covering false-positive status, sanitization, attack feasibility and confidence.

Under a representative workload of 1,500 to 4,000 input tokens and 60 to 100 output tokens per finding, 25 adjudications consume about 37,500 to 100,000 input tokens and 1,500 to 2,500 output tokens.

At GPT-5.6 Sol's published standard rate of $4 per million input tokens and $20 per million output tokens, that costs roughly $0.18 to $0.45 per scan.[2] A cheaper classification model can push the cost lower. GPT-5.6 Luna is listed at $0.20 per million input tokens and $1.20 per million output tokens.[2] Claude Sonnet 5 is currently listed at $2 per million input tokens and $10 per million output tokens.[3]

For 100 monthly scans, the direct LLM bill is therefore closer to $18–$45 with the capped Sol configuration, rather than the $800–$13,600 range reported for repeated full agentic scans.

That is not a discount on the same job. It is a decision to stop buying the wrong job.

## The architecture improves more than cost

The hybrid design makes routine security scanning more predictable.

First, deterministic discovery is repeatable. The same rules against the same revision should produce the same candidates. That matters in a build gate, where "the model felt differently this time" is not an acceptable control.

Second, the LLM is advisory. It can reduce noise and surface uncertainty, but it does not get unilateral authority to pass a build. High-confidence policy decisions remain deterministic, and ambiguous cases go to a reviewer.

Third, the blast radius is smaller. The model does not need build credentials or unrestricted access to the CI runner. It receives the minimum evidence needed to judge a finding. Source code is still sensitive, but reducing the submitted context reduces unnecessary exposure.

Fourth, reviewed decisions compound. A signed suppression bundle records accepted false positives in the repository. Future scans can apply that decision before invoking the model. Teams stop paying repeatedly for the same adjudication, while signatures prevent an untrusted suppression from quietly hiding a finding.

This is the part most "AI security" pitches miss. The hard problem is not getting a model to produce an opinion. It is turning that opinion into a bounded, reviewable and durable control.

## Cheap does not mean equivalent

There is an important limit to this argument: AutoTriage does not replace a full semantic audit.

A model reviewing selected SARIF evidence can only reason about candidates the underlying scanner found. It may miss business-logic flaws, architectural trust-boundary failures and vulnerability classes outside the rule set. The RealVuln results reinforce this point: general-purpose LLM scanners achieved much higher recall-weighted scores than rule-based tools on the benchmark, particularly for vulnerability classes that require semantic understanding.[1]

The benchmark itself also needs qualification. Its first version covers 26 intentionally vulnerable Python repositories, uses F3 as its primary metric, and was authored by the team behind one of the evaluated specialized scanners. The authors disclosed that conflict and published the artifacts, but the results should be treated as reproducible evidence rather than universal procurement truth.[1]

The answer is not to choose one scanner and pretend the other failure modes disappeared. Use each technique at the cadence its economics and evidence quality justify.

## A practical operating model

Run deterministic SAST on every pull request. It is fast, repeatable and cheap enough to sit in the normal delivery path.

Apply verified suppressions before calling an LLM. A suppression should have an owner, rationale, expiry or review condition, and integrity protection. A text file full of unaudited ignores is not governance.

Send only new, changed or low-confidence findings to the model. Bound the number of findings, trace depth, context lines, output schema, retries and total token budget. If the limit is reached, preserve the remaining findings rather than silently treating them as safe.

Route uncertain decisions to a human reviewer. Record the evidence, model and prompt version behind each recommendation so the decision can be reproduced and challenged.

Run a deeper agentic audit periodically: before a major release, after a material architecture change, or on the small set of repositories where compromise would be expensive. That is where broad repository exploration earns its cost.

## Spend intelligence where judgment is required

Sending 100,000 lines of code to a frontier model on every build is easy to demonstrate and hard to operate. It creates variable cost, variable latency and variable conclusions.

A better system separates detection from judgment. Deterministic tools search broadly. Signed policy captures decisions already made. LLMs investigate the findings where local context matters. Humans retain authority over ambiguity. Periodic deep audits cover the gaps that scoped triage cannot.

The goal is not to minimize token spend at any cost. It is to maximize security evidence per dollar without turning the build pipeline into an expensive experiment.

That is the case for AutoTriage: fewer tokens, tighter trust boundaries, durable decisions and an operating model that can survive real CI volume.

## Sources

[1] https://arxiv.org/html/2604.13764v1 — RealVuln benchmark
[2] https://developers.openai.com/api/docs/pricing — OpenAI API pricing
[3] https://docs.anthropic.com/en/docs/about-claude/pricing — Anthropic API pricing
