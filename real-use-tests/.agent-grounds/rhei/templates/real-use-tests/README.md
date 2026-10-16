# Real-use tests template

This Rhei template generates the real-use project for one supported library
version (§FS-real-use-test-generation); its states, programs, and exit codes
are §AR-real-use-test-workflow.

From `real-use-tests/`, check the host, claim the next queued `real-use-test`
issue, and run it with the dashboard (§AR-real-use-test-launcher):

```console
./run-real-use-tests.sh
```

To run a specific issue without claiming it, render a workspace under the
ignored `runs/` folder and run it:

```console
rhei instantiate real-use-tests \
  --set issue_number=10900 \
  --output runs/real-use-10900
rhei run runs/real-use-10900
```

The defaults assume that layout: the source checkout is `../../..` from the
workspace and the run worktree is created under `runs/worktrees/`. Check a
change to the template without running anything:

```console
rhei instantiate real-use-tests --values .agent-grounds/rhei/templates/real-use-tests/.example-values.yaml --dry-run
```

Only `preflight`, `generate`, `native-agent-fix`, and `agent-publication-fix` run an
agent. Every other state runs
`../../programs/real_use.py` from the source checkout, so a program fix takes
effect for the next run without re-rendering the workspace, and the worktree is
branched from the checkout's HEAD — commit the harness and template before a
run that needs them.
