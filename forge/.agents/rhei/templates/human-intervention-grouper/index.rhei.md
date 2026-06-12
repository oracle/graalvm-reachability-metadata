# Rhei: Human Intervention Grouper
**States:** human-intervention-grouper

## Overview
Scan the open `{{source}}` in `{{repo}}` carrying `human-intervention`, group
the open items by the root cause that earned the label — skipping any already
handled in an earlier pass — and open one tracking issue per new group.

The scanner starts in `sleep` and uses incremental backoff:
`{{base_sleep}} + (visit_count - 1) * {{sleep_increment}}`. Each scan pass
writes inventory and grouping artifacts under `runtime/scan/`, updates
`runtime/scan/seen.txt`, and appends one independent opener task for every
new, non-duplicate group. Opener tasks run concurrently under
`rhei run --parallel N`.

This workspace only scans, groups, and opens tracking issues. It must not fix
items, route remediation work, merge pull requests, or mutate labels on the
source `{{source}}`.
