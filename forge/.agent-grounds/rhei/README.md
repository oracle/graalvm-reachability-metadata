# Rhei home

- `templates/forge-metadata/` — the template that resolves one Forge issue
  (§AR-forge-workflow-pipeline); its prompts are §AR-forge-workflow-prompts.
- `templates/code-coverage-improvement/` — the code coverage improvement
  workflow (§AR-code-coverage-improvement).
- `templates/human-intervention-scanner/` — groups `human-intervention` items
  by common cause and files tracking issues.
- `programs/` — the programs the `forge-metadata` states run, one subcommand of
  `forge_run.py` per program state, and their tests
  (§AR-forge-workflow-pipeline.4). They sit outside the template because Rhei
  renders every template file into each workspace.

Run the program tests from `programs/` with
`python3 -m unittest discover -s tests`. Check a template change without
running anything, from `forge/`:

```console
rhei instantiate forge-metadata --values .agent-grounds/rhei/templates/forge-metadata/.example-values.yaml --dry-run
```
