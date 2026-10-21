# FS-forge-run-continuation: Run continuation (retired)

§GOAL-shorten-issue-to-shipped-metadata

Retired. Forge keeps no continuation marker: Rhei resumes a run at the state it
stopped in, and the run branch holds every checkpoint a later program measures
or restores against (§AR-forge-workflow-pipeline). This declaration stays only
until the last citation of it is removed with the code it describes.

## 1. Phase model

Retired: a run's location is its Rhei state (§AR-forge-workflow-pipeline.3).

## 2. ContinuationMarker

Retired: the run state is `runtime/forge/state.json` in the Rhei workspace.

## 3. Resume flow

Retired: `rhei run` on the same workspace resumes the run.
