Task:
- Fix the runtime test failures for library version `{updated_library}`. Test was initially written for version `{old_version}`.
- The test compiles successfully but fails at run time against the new library version.

Source context:
{source_context_overview}

How to use the source context:
- Focus on source files for the classes explicitly named in the Gradle error output below.
- Look for API changes, including renamed methods, changed signatures, removed classes, changed behavior, or new exceptions that explain the runtime failures.
- Stop inspecting sources once you understand the cause of each failure. Then make the minimal edit.

Rules:
- Only edit files that are added to context. Modify `{build_gradle_file}` only if additional dependencies are required.
- Test that is fixed must maintain functional coverage. Never simplify the test to the point of triviality.
- Every individual test must complete in under 60 seconds. If the failure is a timeout or deadlock, use the provided stacktrace/thread dump to replace unbounded waits with bounded waits and close all clients, servers, executors, and other background resources.
- Keep the test in `{test_language_display_name}` under `src/test/{test_source_dir_name}`.
- Follow idiomatic `{test_language_display_name}` coding conventions.
- Use only the provided library version and avoid all deprecated APIs.

Runtime error output:
{initial_error}
