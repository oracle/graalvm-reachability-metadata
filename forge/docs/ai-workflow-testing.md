# AI Workflow Testing Specification

This document is the source of truth for testing AI workflows in this repository.

The main goal is to validate real workflow behavior, not just isolated Python unit behavior. Mock-based tests can still exist for narrow contracts, but they do not prove that an AI workflow is healthy.

## Scope

This spec applies to any AI workflow test in this repository where an agent is asked to run a real workflow with a real strategy.

Rules:

- Test the workflow the user asked you to test.
- Use the strategy the user asked you to use.
- Do not replace a real workflow run with mock-only validation.
- Apply the review process in this document to the actual workflow stages that run.

## Test Input Selection

Always select a real target library from the `oracle/graalvm-reachability-metadata` GitHub issues list with the `library-new-request` label.

Rules:

- Use the same library coordinates for every compared agent when comparing agents.
- Prefer real unsolved libraries over toy examples or already-supported libraries.
- Do not treat mock repositories or synthetic libraries as workflow acceptance coverage.

## Required Runs

Run only the workflow and model or strategy combination requested in the user prompt.

If the user asks for a comparison between agents, run each requested agent on the same library through the same workflow.

If the user asks to test only one workflow, one model, or one strategy, do that exact run and apply the same review standard.

## Codex Review Procedure

Codex runs are slower and require active inspection while the workflow is in progress.

During a Codex run:

- Wait for the generation to finish instead of assuming a fast turnaround.
- Watch for a timeout. A timeout is a workflow defect signal, not something to ignore.
- If Codex times out, assume the prompt may be too loose or the model may be diverging unless there is strong evidence of an external infrastructure problem.
- While a later generation is running, inspect the logs from the previous completed generation instead of waiting passively.

Inspect the agent session logs under `logs/<group>:<artifact>:<version>/<task-type>/` after each completed turn.

In each conversation log, check that:

- the conversation stays focused on the active library and the current failure
- the generation logic is coherent and follows the workflow loop
- the agent is not doing unrelated repo exploration or random edits
- the agent is not loading unnecessary skills or unrelated context
- the prompt and response format are clean and usable for debugging
- the model reacts to Gradle feedback instead of repeating the same idea without evidence

## Native Image and Metadata-Fix Review

The add-new-library workflow reaches the metadata stage when Gradle hits `nativeTest` or when unit tests otherwise progress to metadata generation.

Current expected flow:

1. the workflow reaches `nativeTest`
2. it runs `generateMetadata`
3. it reruns `./gradlew test -Pcoordinates=<library>`
4. if the rerun still fails, workflow infrastructure calls `ai_workflows/fix_metadata_codex.py`

When the metadata-fix fallback runs, inspect `logs/<group>:<artifact>:<version>/metadata-fix/codex.log`.

For the metadata-fix log, verify that:

- the run stays focused on the missing metadata problem
- the `fix-missing-reachability-metadata` skill usage is relevant and not replaced by random exploration
- the proposed metadata changes match the failing native image evidence
- the run does not time out
- the workflow does not drift into unrelated test or repository changes

If the metadata-fix stage times out, treat that as a concrete workflow problem and review the log before changing prompts or strategy settings.

## End-of-Run Validation

At the end of every run, inspect the workflow outputs instead of relying only on process exit code.

Required checks:

- the generated test suite is valid, cohesive, and meaningful for the target library
- the workflow did not introduce unrelated edits
- the metadata output is present and consistent with the test outcome
- the metrics file looks sane for the run
- nothing in the generated output suggests the agent solved the task by breaking or trivializing the test

Review the run metrics file at:

- `<metrics_repo_path>/script_run_metrics/add_new_library_support.json`, with successful run metrics committed to `stats/<group>/<artifact>/<version>/execution-metrics.json`
- `<metrics_repo_path>/benchmark_run_metrics/add_new_library_support.json` when running benchmark mode

Inspect at least these fields:

- `status`
- `strategy_name`
- `metrics.input_tokens_used`
- `metrics.cached_input_tokens_used` when present
- `metrics.output_tokens_used`
- `metrics.iterations`
- `metrics.cost_usd`
- `metrics.generated_loc`
- `metrics.tested_library_loc`
- `metrics.code_coverage_percent`
- `metrics.metadata_entries`
- `artifacts.test_file`
- `artifacts.metadata_file`

Coverage and other metrics do not need a single universal threshold, but they must look credible for the library size and the generated test shape. Suspiciously low coverage, near-empty tests, or inflated metadata with weak tests should be treated as failures that need investigation.

## Pass Criteria

A workflow test passes only if all of the following are true:

- it uses a real library from the `library-new-request` issue queue
- the real workflow entrypoint was executed
- the generated test logic is valid and meaningful
- Codex logs show grounded, non-random behavior
- no timeout was ignored
- if metadata-fix ran, its log also looks valid
- metrics and artifacts are present and credible

## Fail Criteria

A workflow test fails if any of the following happen:

- evaluation depends only on mocked or synthetic tests
- Codex times out and the timeout is not investigated through logs
- conversation logs show drift, random actions, or unnecessary skill loading
- metadata-fix times out or behaves incoherently
- generated tests are trivial, broken, or misleading
- metrics indicate broken output or obviously suspicious results

## Relationship to Unit Tests

Unit tests for agent adapters, strategy loading, and small helpers are still useful, but they are secondary checks.

They do not replace:

- running the real workflow
- reviewing Codex conversation logs
- reviewing metadata-fix logs
- validating final metrics and artifacts on a real library

## Output Expectations

When testing is finished, report the findings in the response.

Rules:

- summarize what was run
- describe the important observations from logs, generated tests, and metrics
- call out anything that looks wrong or suspicious
- do not make instant workflow or prompt changes as part of the test unless the user explicitly asks for implementation changes
