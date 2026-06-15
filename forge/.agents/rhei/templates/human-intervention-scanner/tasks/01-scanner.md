### Task scanner: Scan and track human-intervention {{source}}
**State:** sleep

One recurring task that scans, groups, then opens tracking issues in sequence.
On each pass it backs off, scans the open `{{source}}` in `{{repo}}` labeled
`human-intervention` (skipping items already handled, recorded in
`runtime/scan/seen.txt`), and writes a report at `runtime/scan/groups.md`
explaining the system issue behind each group. The opener step runs only after
the scan finishes: it reads that one report and opens a GitHub tracking issue
per group, reusing any similar issue that is already open instead of creating a
duplicate. It never mutates the source `{{source}}` items.
