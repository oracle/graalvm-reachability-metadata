Task:
- Fix the runtime test failures for library version `{updated_library}`. Test was initially written for version `{old_version}`.
- The test compiles successfully but fails at run time against the new library version.

Source context:
{source_context_overview}

Library Preparation Preflight:
{library_preparation_preflight_context}

How to use the source context:
- Focus on source files for the classes explicitly named in the Gradle error output below.
- Look for API changes, including renamed methods, changed signatures, removed classes, changed behavior, or new exceptions that explain the runtime failures.
- Stop inspecting sources once you understand the cause of each failure. Then make the minimal edit.

Rules (test contract: §root/FS-test-contract):
- The binding test contract is `docs/functional-spec/test-contract.md` in this repository; re-read the sections a failure cites before fixing.
- Only edit files that are added to context. Modify `{build_gradle_file}` only if additional dependencies are required (§root/FS-test-contract.3.4). §AR-forge-strategy-agent-boundary
- Test that is fixed must maintain functional coverage. Never simplify the test to the point of triviality. §root/FS-test-contract.3.6
- If the failure is a timeout or deadlock, use the provided stacktrace/thread dump to replace unbounded waits with bounded waits within the contract's timeout bounds. §root/FS-test-contract.1.6

Runtime error output:
{initial_error}
