Task:
- Fix the tests for library version `{updated_library}`. Test was initially written for version `{old_version}`.
- The test currently fails to compile or run against the new library version.

Source context:
{source_context_overview}

How to use the source context (strict):
- Only open source files that correspond or are related to the classes explicitly named in the Gradle error output below.
- Stop inspecting sources as soon as you have identified the renamed/removed/changed API for the failing symbols. Then make the minimal edit to the test.

Rules:
- Only edit files that are added to context. Modify `{build_gradle_file}` only if additional dependencies are required.
- Test that is fixed must maintain functional coverage. Never simplify the test to the point of triviality.
- Every individual test must complete in under 60 seconds. Use bounded waits and close all clients, servers, executors, and other background resources.
- Keep the test in `{test_language_display_name}` under `src/test/{test_source_dir_name}`.
- Follow idiomatic `{test_language_display_name}` coding conventions.
- Use only the provided library version and avoid all deprecated APIs.

Initial Gradle error output:
{initial_error}
