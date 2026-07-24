# FS-repository-status-report: Repository issue progress and state

Maintainers and agents must be able to inspect the repository's unresolved issue
state from one deterministic report. The report measures both the current
weighted backlog and the time that unresolved work has accumulated, helping
maintainers shorten the path from an issue to shipped metadata while continuing
to broaden tested library coverage.
§GOAL-broad-version-coverage §forge/GOAL-shorten-issue-to-shipped-metadata

## 1. Issue scope and priority

The report includes every open GitHub issue in
`oracle/graalvm-reachability-metadata` and excludes pull requests. Priority is
classified into exactly one tier, in this order:

1. `high`: the issue has the exact `high-priority` label; weight `10`.
2. `priority`: the issue does not have `high-priority` and has the exact
   `priority` label; weight `5`.
3. `normal`: the issue has neither priority label; weight `1`.

An issue carrying both labels is classified as `high` and reported as a
data-quality warning. Priority and age remain independent facts: age never
changes an issue's label-derived tier.

## 2. Age and debt

The unresolved age is the number of complete UTC days between an issue's
`createdAt` value and report generation, clamped to zero. This is the issue's
full lifetime and not the duration since its most recent reopening.

Each open issue contributes its priority weight to the weighted backlog and its
priority weight multiplied by its age in days to weighted age debt. Reports
must provide both totals and per-tier values.

Standing priority pressure is summarized as **priority age debt**: the weighted
age debt of the `high` and `priority` tiers only, excluding `normal`. A single
neglected high- or priority-labelled issue must dominate this figure, while a
large pile of aging `normal` issues must not. The human report renders it as a
fixed-scale fill meter from `0` to the policy's age-debt meter maximum, placed
directly below the recent-flow meter; values at or above the maximum saturate
the meter at full. The recent flow and the priority age debt are independent
signals: flow tracks whether the backlog is shrinking this window, while
priority age debt tracks how much neglected high-value work has accumulated.

Age bands are inclusive:

| Band | Age |
| --- | --- |
| `fresh` | 0–7 days |
| `aging` | 8–30 days |
| `old` | 31–90 days |
| `stale` | 91 days or more |

The attention queue is ordered by priority tier (`high`, `priority`, `normal`),
then oldest first, then issue number. Thus age exposes neglected work without
allowing an old normal issue to outrank high-priority work.

## 3. Project state and recent flow

When GitHub exposes the issue's item in the configured repository project, the
report records its `Todo`, `In Progress`, or `Done` status. Open issues reported
as `Done` and issues carrying both priority labels remain in the backlog and are
surfaced as data-quality warnings. Issues with an unknown or missing project
status are still counted in the project-state totals but are not warned about.

For a configurable recent window, defaulting to 30 days, the report measures:

- issues opened during the window;
- issues resolved during the window;
- the corresponding priority-weighted opened and resolved totals;
- weighted net change (`opened - resolved`); and
- weighted burn ratio (`resolved / opened`), represented as `null` when no
  weighted issues were opened.

These flow measures describe recent progress; the weighted backlog and age debt
describe current state.

The human report represents recent flow on a fixed 0–100 meter:

`weighted resolved / (weighted opened + weighted resolved) × 100`.

Zero means all measured pressure came from opened work, 50 means weighted
arrivals and resolutions were balanced, and 100 means all measured pressure
went toward resolutions. When neither occurred, the meter rests at 50.

## 4. Human and agent representations

The command exposes exactly one required representation mode:

- `--human` writes a self-contained HTML report whose first and dominant section
  is priority pressure, followed by the recent-flow meter and the ordered
  attention queue. It must not put a general unresolved-issue summary ahead of
  priority pressure. Data-quality warnings remain compact and secondary.
- `--agent` writes a stable, schema-versioned JSON document containing numeric
  values, explicit calculation policy, warnings, and the ordered issue records.

Both representations must be rendered from the same normalized snapshot and
measurements. In agent mode stdout contains only the JSON document; diagnostics
go to stderr. Fetch or validation failures return a non-zero exit status.

# AR-repository-status-tool: Repository status tool

The repository status implementation lives in the root
`repository_status/` directory as a small Python standard-library application.
It invokes the authenticated GitHub CLI for issue data, normalizes GitHub values
into typed internal records, computes the measurements in pure functions, and
passes one report model to separate HTML and JSON renderers.
§FS-repository-status-report

The versioned policy file owns the repository, project title, priority weights,
age-band boundaries, default flow window, and the age-debt meter maximum. Fixture-based unit tests exercise
classification, age calculations, flow, warning detection, deterministic
ordering, and both renderers without accessing GitHub.
