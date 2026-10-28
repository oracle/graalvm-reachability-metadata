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
Its `--scenario-passes` option, by default the template's 15, is every run's
`scenario_passes`: the launcher rejects a count that is not a positive
integer, as the template does, and passes it to `rhei instantiate`.
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
launcher then claims the next one. Every iteration starts with the host gate,
so a host that broke since the last run, such as an expired `gh` login, a
stopped Docker daemon, or a logged-out agent, ends the launcher before it
claims. When the program finds nothing to claim, the launcher sleeps ten
minutes and claims again. A failed instantiation runs the program's `--release`, which returns the issue to
`Todo` only while the launcher's account is still its sole assignee.

After every third run, and when the program finds nothing to claim, the
launcher runs the `review` program with no arguments
(§FS-real-use-test-generation.4); a failed review prints a warning and the
launcher goes on. The program keeps nothing between reviews: it lists the open
pull requests labeled `real-use-test` with `gh pr list` and walks their
numbers by creation time, oldest first. Each one is read again with
`gh pr view` just before it is judged, so a label, approval, merge, or push
that landed while an earlier agent ran is seen. It skips one that is no
longer open, one whose review decision is `APPROVED`, one that carries
`graalvm-bug`, and one that carries the `in-review` label unless the latest
`labeled` event for that label in the issue timeline is more than three hours
old. For any other pull request it adds `in-review`, adds a detached worktree
of the head commit, and runs the worker agent once in it, non-interactively:
`claude -p`, with the target's model and effort, or `pi -p`, with its
provider, model, and thinking level. The prompt names only the pull request
number, branch, and head, and it never waits for running checks. The worktree
is removed afterwards, and the label is removed as a plain step once the agent
process has returned, so a launcher killed mid-review leaves a label that goes
stale after three hours. The program stops after three reviewed pull requests
or at the end of the list, and does not check what the agent did.

The label keeps concurrent launchers off the same pull request. Adding it is
not atomic, so two launchers reading one pull request at once can both
review it; that rare double review is accepted, because a second approval changes nothing
and a second push is rejected as non-fast-forward.
