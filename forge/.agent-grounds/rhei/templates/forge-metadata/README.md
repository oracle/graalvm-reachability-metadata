# Forge metadata template

This Rhei template resolves one Forge issue; its states, programs, exit codes,
and the loop guard are §AR-forge-workflow-pipeline, and what each agent state
is told is §AR-forge-workflow-prompts.

From `forge/`, check the host, claim the next eligible issue, and run it with
the dashboard (§AR-forge-workflow-pipeline.1):

```console
./run-forge.sh
```

To run a specific issue the launcher already claimed, render a workspace under
the ignored `runs/` folder and run it:

```console
rhei instantiate forge-metadata issue_number=10950 --output runs/forge-10950
rhei run runs/forge-10950 --dashboard
```

The defaults assume that layout: the source checkout is `../../..` from the
workspace and the run worktree is created under `runs/worktrees/`. Only
`setup`, `cover`, and `fix` run an agent. Every other state runs
`../../programs/forge_run.py` from the source checkout, so a program fix takes
effect for the next run without re-rendering the workspace.
