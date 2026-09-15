Task:
- Fix the tests for library version `{updated_library}`. Test was initially written for version `{old_version}`.
- The test currently fails to compile or run against the new library version.

Source context:
{source_context_overview}

Library Preparation Preflight:
{library_preparation_preflight_context}

How to use the source context (strict):
- Only open source files that correspond or are related to the classes explicitly named in the Gradle error output below.
- Stop inspecting sources as soon as you have identified the renamed/removed/changed API for the failing symbols. Then make the minimal edit to the test.

Rules (test contract: §root/FS-test-contract):
- The binding test contract is `docs/functional-spec/test-contract.md` in this repository; re-read the sections a failure cites before fixing.
- Only edit files that are added to context. Modify `{build_gradle_file}` only if additional dependencies are required (§root/FS-test-contract.3.4). §AR-forge-strategy-agent-boundary
- Test that is fixed must maintain functional coverage. Never simplify the test to the point of triviality. §root/FS-test-contract.3.6

Initial Gradle error output:
{initial_error}
