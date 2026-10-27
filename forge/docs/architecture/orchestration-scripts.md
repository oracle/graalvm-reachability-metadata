# AR-forge-orchestration: Forge orchestration scripts (retired)

§FS-forge-issue-resolution-goal

The dispatcher's queue scan, routing, preflight, and review queues are gone:
the launcher of §AR-forge-workflow-pipeline.1 fetches, claims, checks the form,
and starts one Rhei run, and its review of open pull requests is
§FS-published-pr-review. This declaration stays only until the last citation
of it is removed.

## 1. Library-Specific Preparation Decision

Retired: the `setup` agent state declares what the tests need, and the setup
tail keeps only what it may change (§AR-forge-workflow-pipeline.4.1).
