### Task scanner: Scan human-intervention {{source}}
**State:** sleep

Periodically scan open `{{source}}` in `{{repo}}` labeled `{{label}}`, group
unscanned items by the root cause that earned the label, and append one opener
task per new, non-duplicate group. The scanner writes runtime artifacts under
`runtime/scan/` and never mutates source item labels.
