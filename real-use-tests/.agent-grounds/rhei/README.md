# Rhei home

- `templates/real-use-tests/` — the template that generates and publishes
  real-use test projects (§FS-real-use-test-generation).
- `programs/` — the programs its states run, one subcommand of `real_use.py`
  per program state, and their tests (§AR-real-use-test-workflow.4). They sit
  outside the template because Rhei renders every template file into each
  workspace.

Run the program tests from `programs/` with
`python3 -m unittest discover -s tests`.
