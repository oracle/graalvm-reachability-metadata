# AR-code-coverage-trajectory: Per-pass coverage trajectory

Each coverage loop of the code coverage workflow (§AR-code-coverage-improvement)
declares its pass yield as a Rhei metric, so every run carries one readable
table per loop: which pass moved coverage, by how much, and which agent sessions
ran in that pass. Without it the trajectory is spread across one numbered report
per pass and a one-line phase verdict, and telling a productive pass from a
stalled one means opening every report. A stalled pass is coverage the run did
not add (§GOAL-maximize-library-coverage) and a full agent invocation spent on
nothing (§GOAL-minimize-generation-cost), so it has to be visible at a glance.

## 1. Declaration

`states.yaml` declares one metric per loop, measured by that loop's measure
state and driven by its cover state:

| Metric | Measured by | Driver | Boundary artifact | Value |
| --- | --- | --- | --- | --- |
| `api-coverage` | `api-measure` | `api-cover` | `validation/api-cover-report-{iteration}.json` | `/summary/covered` |
| `deep-coverage` | `deep-measure` | `deep-cover` | `discovery/discovery-report-{iteration}.json` | `/summary/rosterCovered` |
| `naive-coverage` | `naive-measure` | `naive-cover` | `validation/naive-cover-report-{iteration}.json` | `/summary/covered` |

Paths sit under `runtime/code-coverage/`. Each value is the covered count the
stop rule reads for that phase (§AR-code-coverage-improvement.4.3), so a row's
delta is that pass's yield and matches the phase verdict's last yields. The
detail beside a value names the roster it counts on; the deep detail splits the
roster into internal methods and the public methods the API phase left.

## 2. What Rhei records

A measure run confirms an iteration when its numbered report exists. Rhei then
appends the value, with every agent session of the loop task since the previous
measurement, to `runtime/metrics/<metric>.jsonl`, and renders the trajectory to
`runtime/reports/metrics-summary.md` and a before/after strip into each bound
session report. Rhei counts iterations from 0, as measurement numbers its
reports, so row 0 is the phase baseline and the two counters agree.

## 3. Boundaries

- **Not a published figure.** A row counts on its phase's own roster, which
  §AR-code-coverage-improvement.5.1 forbids for published coverage, so the
  trajectory carries counts, not ratios. Finalization's checkpoints stay the
  only published figures, and `result.json` is unchanged.
- **No control.** Declared metrics drive no transition; the stop decision still
  reads the reports itself.
- **Retries keep their first row.** A measurement that fails after writing its
  report is recorded from that first attempt; the repair retry rewrites the
  same report and adds no row, and its fix session lands in the next pass's
  window.
- **Deltas are raw.** A pass that loses coverage shows a negative delta, where
  the stop rule clamps it to zero yield.
