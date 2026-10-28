# AR-forge-drivers: Workflow drivers (retired)

§FS-forge-issue-resolution-goal

The per-queue driver scripts are gone: `prepare` adds the version and the
programs of §AR-forge-workflow-pipeline.4 do what the drivers did. The
declarations below stay only until the last citation of each is removed.

## AR-forge-driver-queues: The per-queue drivers (retired)

Retired: the route is the issue label (§AR-forge-control-plane).

### 1. New library

Retired: `prepare` scaffolds it.

### 2. Library update

Retired: `prepare` adds the version beside its nearest supported one (§FS-library-update-tested-version-split).

#### 2.1 The reporter's requested metadata

Retired: `prepare` keeps the reporter's issue body, and the first cover prompt lists it (§AR-forge-workflow-prompts.4).

### 3. Java failures

Retired: `repair` runs the version, and `publish` opens the follow-up of a deferred exploration (§AR-forge-workflow-pipeline.7).

### 4. Native-image failures

Retired: the native check of §AR-forge-workflow-pipeline.5.

## AR-forge-driver-finalization: Finalization and metrics (retired)

Retired: `finalize` and `publish` of §AR-forge-workflow-pipeline.4 (§FS-forge-issue-resolution-goal).
