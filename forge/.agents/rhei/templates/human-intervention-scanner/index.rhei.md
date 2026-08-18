# Rhei: Human Intervention Scanner
**States:** human-intervention-scanner

## Overview
One recurring scanner task drives `{{repo}}` through
`sleep -> queue-check -> scan -> sleep`. The `sleep` state only backs off. The
`queue-check` state performs the cheap GitHub availability check before spending
an agent run. The scanner never opens GitHub issues. It only scans the open
`{{source}}` carrying `human-intervention`, extracts the direct reason each item
got the label, groups common causes, and writes group reports.

Only groups with at least `{{min_group_size}}` source items get generated
investigation tasks. Smaller groups are recorded in `runtime/scan/groups.md` as
insufficient-support findings and are not opened as tracker issues.

Each generated group task reads its own group file under `runtime/scan/groups/`
and searches the current repository checkout for a still-current system cause.
If the behavior is stale, already fixed, one-off, library-specific, or not tied
to current system code, that group task cancels without opening anything. If it
finds a current system problem, it writes an issue body with file evidence,
proposed fix, and verification guidance.

The opener state runs only for investigation tasks that wrote `Decision:
open-issue`. It creates or reuses one GitHub issue from the prepared issue body
and applies `{{issue_labels}}`.

The `sleep` state uses incremental backoff based on scan attempts:
`{{base_sleep}} + (sleep_visit_count - 1) * {{sleep_increment}}`. The `scan`
state is capped at `{{max_scans}}` passes, and the scanner completes once the
queue is drained or the scan cap is reached. The scanner records scanned items
in `runtime/scan/seen.txt`.

This workspace must not fix source items, route remediation work, merge pull
requests, or mutate labels on the source `{{source}}`.
