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
- Only edit files that are added to context. Modify `{build_gradle_file}` only if additional dependencies are required. §root/FS-test-contract.2.9
- Test that is fixed must maintain functional coverage. Never simplify the test to the point of triviality. §root/FS-test-contract.3.6
- Every individual test must complete in under 60 seconds. Use bounded waits and close all clients, servers, executors, and other background resources. §root/FS-test-contract.1.6
- Keep the test in `{test_language_display_name}` under `src/test/{test_source_dir_name}`. §root/FS-test-contract.1.1
- Follow idiomatic `{test_language_display_name}` coding conventions. §root/FS-test-contract.1.2
- Use only the provided library version. §root/FS-test-contract.1.4

Initial Gradle error output:
{initial_error}
