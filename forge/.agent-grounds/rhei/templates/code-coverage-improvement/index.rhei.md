# Rhei: Code Coverage Improvement

## Rules

- Every turn is headless: your turn ending is the process exiting, and no
  later turn wakes up to collect anything. Run every Gradle command in the
  foreground with a timeout long enough to finish it; never start one in the
  background and end the turn to wait for it.
- Write the output your state names before your final message. A turn that
  ends with it unwritten discards the whole invocation.
- Relative artifact paths are relative to the Rhei workspace root, the
  directory holding `states.yaml` and `index.rhei.md`, which is
  `{{workspace_path}}` from your working directory. Resolve each against
  it; never prefix one with the workspace directory name alone or guess a
  prefix from your working directory.
- Never edit `index.rhei.md`, `states.yaml`, or anything under `tasks/`: Rhei
  owns the plan, and your state's output file is your only report.
