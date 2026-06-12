### Task scanner: Scan human-intervention {{source}}
**State:** sleep

Periodically scan the open `{{source}}` in `{{repo}}` labeled
`human-intervention`. On each pass, group the open items by the root cause that
earned the label, skip any already handled in an earlier pass (recorded in
`runtime/scan/seen.txt`), and append one opener task per new, non-duplicate
group. The scanner writes runtime artifacts under `runtime/scan/` and never
mutates source item labels.
