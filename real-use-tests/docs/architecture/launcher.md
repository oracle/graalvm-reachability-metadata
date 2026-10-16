# AR-real-use-test-launcher: Real-use run launcher

`real-use-tests/run-real-use-tests.sh` starts runs of the workflow
(§AR-real-use-test-workflow) for the queued issues, one after another, until it
is stopped (§FS-real-use-test-generation.1). It sources Forge's shared Rhei launcher
preflight, `forge/utility_scripts/rhei_launcher_host.sh`, which the
code-coverage launcher sources too: it checks `gh` 2.39.0 or newer, `python3`,
and `rhei`, then runs Forge's host gate in its coverage mode
(§forge/FS-forge-host-requirements) for the worker agent.

The worker agent is the launcher's `--worker-agent` Rhei target, by default
the template's `pi[high]:openai-codex/gpt-5.6-luna`, and the launcher passes
it to the run unchanged. It reads the agent and the model from the target and
rejects any agent but `pi` and `claude-code` before the gate. The gate then
checks that agent as Forge checks its analysis agent: `pi` on the path and
logged in to the target's provider, or `claude` on the path with a passing
`claude auth status`; neither probe invokes a model.
Before that gate the launcher fills whichever of `JAVA_HOME` and
`GRAALVM_HOME` is unset from the other and rejects two different
installations.

It then runs the `claim` program with Forge's interpreter and Forge on the
path. The program lists the candidates with `gh issue list`, orders them as
the code-coverage launcher does (`high-priority`, then `priority`, then the
rest, newest first), skips a title that does not name exactly one coordinate,
and calls Forge's `try_claim_issue` on each in turn; that call holds Forge's
local per-issue lock across the assign, wait, and verify, so a Forge worker on
the same host cannot take the issue at the same time. The first claimed issue
is instantiated into `runs/real-use-<issue>` and run with the dashboard, and the
launcher then claims the next one. When the program finds nothing to claim,
the launcher sleeps ten minutes and claims again; a third failed run in a row
ends it. A
failed instantiation runs the program's `--release`, which returns the issue to
`Todo` only while the launcher's account is still its sole assignee.

A run that pushes a branch records it in
`runtime/real-use/publication/branch.md`, and the launcher collects those
branches. After every third run, when the queue is empty, and before it stops
on failures, it hands the collected branches to the `review` program
(§FS-real-use-test-generation.4). For each branch the program reads the pull
request once with `gh pr list --head`; a branch without one yet goes back to
the launcher for the next batch. It skips a pull request that is approved,
merged, or closed. For any other pull request it adds a detached worktree of the head
commit and runs the worker agent once in it, non-interactively: `claude -p`,
with the target's model and effort, or `pi -p`, with its provider, model, and
thinking level. The prompt names only the pull request number, branch, and
head, and it never waits for running checks. The worktree is removed
afterwards. A branch whose head moved during the review goes back to the
launcher too, because the agent pushed a fix the next batch must see.
