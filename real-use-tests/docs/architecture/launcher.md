# AR-real-use-test-launcher: Real-use run launcher

`real-use-tests/run-real-use-tests.sh` starts one run of the workflow
(§AR-real-use-test-workflow) for the next queued issue
(§FS-real-use-test-generation.1). It sources Forge's shared Rhei launcher
preflight, `forge/utility_scripts/rhei_launcher_host.sh`, which the
code-coverage launcher sources too: it checks `gh` 2.39.0 or newer, `python3`,
and `rhei`, then runs Forge's host gate in its coverage mode
(§forge/FS-forge-host-requirements) for the template's default worker agent.
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
is instantiated into `runs/real-use-<issue>` and run with the dashboard. A
failed instantiation runs the program's `--release`, which returns the issue to
`Todo` only while the launcher's account is still its sole assignee.
