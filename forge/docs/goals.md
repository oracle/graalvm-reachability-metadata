# GOAL-forge-direction: Where: Forge direction and outcomes

Forge should provide coverage-positive automation for the
graalvm-reachability-metadata repository (§GRUND-forge-motivation).
Its direction is measured by four primary outcomes: maximize practical JVM
library coverage (§GOAL-maximize-library-coverage), shorten the path from opened
issue to shipped metadata (§GOAL-shorten-issue-to-shipped-metadata), and
minimize generation cost without weakening quality or reviewability
(§GOAL-minimize-generation-cost), and turn recurring failures into better
automation (§GOAL-improve-automation-first).

# GOAL-maximize-library-coverage: Maximize library coverage

Forge should make supported JVM library coverage as high as practical. It should
add metadata and tests for unsupported libraries, improve coverage for
already-supported libraries, and preserve and protect the current state of
shipped metadata against regressions or weakened metadata contracts, serving
§GOAL-forge-direction.

# GOAL-shorten-issue-to-shipped-metadata: Shorten issue-to-metadata delivery

Forge should make the path from a supported opened issue to shipped metadata as
short as practical. Supported issue labels should route to clear, documented,
reliable workflows that produce locally verified, review-ready pull requests and
preserve enough evidence for maintainers or later Forge runs to continue without
rediscovering the same problem, serving §GOAL-forge-direction.

# GOAL-minimize-generation-cost: Minimize generation cost

Forge should minimize the cost of generation while preserving correctness,
coverage, and reviewability. It should use the least tokens and compute needed
for the task, prefer lightweight agents such as Pi when sufficient, measure cost
explicitly, and clear stale agent context between independent steps or runs,
serving §GOAL-forge-direction.

# GOAL-improve-automation-first: Improve automation before one-off fixes

When Forge encounters a problem, maintainers should first look for an
improvement to the automation logic, workflow algorithms, prompts, strategies,
or deterministic harness support that would solve the class of problems. Direct
manual fixes to one library are reserved for high-priority issues that the
automation cannot safely resolve yet, serving §GOAL-forge-direction.
