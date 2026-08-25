# Your LLM does not need to read 100,000 lines of code on every build

We are using frontier AI like an expensive grep command.

Teams are feeding entire repositories into LLM security scanners on every build, then acting surprised when the bill climbs, the pipeline slows down, and the model reaches a different conclusion on Tuesday.

There is a better approach: let deterministic tools search broadly, then spend LLM tokens only where semantic judgment is required.

For a 100,000-line codebase, published benchmark costs for general-purpose LLM security scanning range from roughly $8 to $136 per scan. Run that 100 times a month and the direct scan cost becomes $800 to $13,600.

The AutoTriage approach changes the unit of work.

OpenGrep performs repeatable detection and emits SARIF. Verified suppressions remove findings the team has already reviewed. The LLM receives only the remaining evidence: the weakness class, relevant source locations, a bounded data-flow trace, and a few lines of surrounding code.

With the current default cap of 25 findings, the estimated LLM cost is measured in cents rather than tens or hundreds of dollars. A representative GPT-5.6 Sol run lands around $0.18 to $0.45.

That is a 95% to 99% reduction in routine LLM spend.

But the cost saving is not the most important part.

The model is no longer the scanner, the policy engine, and the build gate rolled into one probabilistic black box. It becomes an adviser with a narrow job: judge whether the evidence supports a finding.

That gives us an operating model that can survive real CI volume:

- Run deterministic SAST on every pull request.
- Apply governed, integrity-protected suppressions before invoking a model.
- Send only new or ambiguous findings to the LLM.
- Route uncertain decisions to a security reviewer.
- Reserve full agentic audits for major releases and high-risk systems.

There is a trade-off. Scoped triage cannot discover a vulnerability that the underlying scanner never found. Business-logic flaws and architectural trust failures still need broader analysis.

So this is not an argument to kill deep LLM security audits. It is an argument to stop running them at the wrong cadence.

A full-repository agent should be a specialist investigation, not a tax on every commit.

The security industry does not need more AI sprayed across the pipeline. It needs better separation between detection, judgment, policy, and human authority.

Use cheap, repeatable machinery for the broad search. Use expensive intelligence where judgment is actually required.

Read the full article, including the cost model, architecture, limitations, and sources:

[FULL ARTICLE](FULL_ARTICLE_URL)

#ApplicationSecurity #AppSec #DevSecOps #LLMSecurity #SAST #AIEngineering #OpenSource
