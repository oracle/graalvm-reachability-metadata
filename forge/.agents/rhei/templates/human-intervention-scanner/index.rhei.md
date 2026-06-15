# Rhei: Human Intervention Scanner
**States:** human-intervention-scanner

## Overview
One recurring task drives `{{repo}}` through three states in sequence —
`sleep → scan → open → sleep` — so the opener step runs strictly after each scan,
without any concurrency:

- **scan** scans the open `{{source}}` carrying `human-intervention`, groups the
  items by the root cause that earned the label, and writes a report
  (`runtime/scan/groups.md`) explaining the system issue behind each group. It
  only investigates and reports — it never touches GitHub issues.
- **open** reads that one report and opens a GitHub tracking issue per group,
  first checking whether a similar tracking issue is already open and reusing it
  instead of creating a duplicate.

The `sleep` state uses incremental backoff
`{{base_sleep}} + (visit_count - 1) * {{sleep_increment}}`, capped at
`{{max_scans}}` passes, and completes once the queue is drained. The scanner
records scanned items in `runtime/scan/seen.txt`; tracking-issue deduplication is
done against open GitHub issues, so no local opener ledger is kept.

This workspace only scans, groups, and opens tracking issues. It must not fix
items, route remediation work, merge pull requests, or mutate labels on the
source `{{source}}`.
