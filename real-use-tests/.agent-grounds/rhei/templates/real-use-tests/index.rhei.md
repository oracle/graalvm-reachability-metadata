# Rhei: Real-use tests for issue #{{issue_number}}

## Rules

- Every turn is headless: your turn ending is the process exiting, and no
  later turn wakes up to collect anything. Run every Gradle command in the
  foreground with a timeout long enough to finish it; never start one in the
  background and end the turn to wait for it.
- Write the report your state names before your final message. A turn that
  ends with it unwritten discards the whole invocation.
- Your working directory is the run worktree. Relative artifact paths are
  relative to the Rhei workspace root; the paths in your prompt are absolute.
- Never edit `index.rhei.md`, `states.yaml`, anything under `tasks/`, or
  anything under `runtime/`: Rhei and the programs own them, and your report is
  your only output besides the files your state lets you change.
