### Task scanner: Scan and group human-intervention {{source}}
**State:** sleep

One recurring task that scans open `{{source}}` in `{{repo}}` labeled
`human-intervention`, skipping items already recorded in
`runtime/scan/seen.txt`. On each scan pass it groups common causes, writes an
aggregate report at `runtime/scan/groups.md`, writes one strict group file per
kept group under `runtime/scan/groups/`, and generates one investigation task
per group with at least `{{min_group_size}}` contributing items.

The scanner does not open GitHub issues. Generated group tasks search the
current codebase for a still-current system problem and cancel themselves when a
group is stale, already fixed, one-off, library-specific, or unsupported by
current repository evidence.
