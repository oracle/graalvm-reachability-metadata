# AR-do-work-loop: do-work loop architecture

The do-work loop is Forge's long-running worker shell, the local entry path
into Forge's issue-resolution responsibility defined in
§FS-forge-issue-resolution-goal. `do-work.sh` is the stable entrypoint and
stays intentionally small: it
forwards `argv` unchanged to `do_up_to_date_work.sh`. `do_up_to_date_work.sh`
owns branch selection, self-update, work limits, review limits, stop-file
handling, sleep timing, and re-execing the latest worker script before the
next cycle.

The worker accepts `--analysis-agent`, `--analysis-model`, `--test-agent`,
`--test-model`, and the optional common `--agent-family`, with matching
`FORGE_*` environment variables. The family and role flags accept the same four
supported backend names; a role-specific flag overrides the family for that
role. The worker validates these names and exports the effective configuration
before every dispatch and re-exec, implementing
§FS-forge-agent-runtime-selection.

Before the first self-update or queue operation in each worker process,
`do_up_to_date_work.sh` validates the host requirements defined in
`utility_scripts/host_requirements.py`. It derives the required capabilities
from the enabled issue and review queue limits, prints the exact tool,
environment, filesystem, network, GitHub, Docker, and agent requirements, and
exits before work on any failed required check (§FS-forge-host-requirements).
The shell gate is deliberately early — it stops a misconfigured worker before it
self-updates or re-execs — and it is not the only one: `forge_metadata.py`
revalidates from the freshly updated checkout at the start of every
work-starting invocation, using the capabilities its invoked mode actually needs.
Keeping the checks in a Python module makes them unit-testable and gives the
requirement one definition for both callers.

The 25.0.x validation lane is pinned in `graalvm-versions.json`; update that
file deliberately when Forge should move to a newer 25.0.x release. The main
and EA lanes are resolved from their upstream latest-release records for every
issue-work startup. Use `--graalvm-version-check warn` (or `off`) to run against
a locally built or patched Graal branch; Native Image and the
reachability-metadata schema stay mandatory in every mode. Run the same gate
without starting work from `forge/`:

```bash
python3 utility_scripts/host_requirements.py --forge-dir . \
  --python-bin python3 --review-model gpt-5.6-luna \
  --graalvm-version-check strict
```

The loop does not own issue semantics. It converts command-line flags and
environment variables into one bounded worker cycle, then delegates queue
selection and workflow routing to the orchestration layer described in
§AR-forge-orchestration. This keeps
local operator controls outside of the Python issue dispatcher
(§AR-forge-control-plane) while preventing individual workflows from learning
about worker sleep, branch monitoring, or stop markers.

When invoked with `--user-requested-only`, or with
`FORGE_USER_REQUESTED_ISSUES_ONLY=1`, the worker asks orchestration to fetch
only user-requested issue queue items. The filter is applied by the dispatcher,
not by individual workflow drivers (§AR-forge-orchestration).

The do-work loop is architectural rather than behavioral: it does not need a
separate component functional spec unless worker semantics grow beyond
bootstrap, self-update, and cycle scheduling.
