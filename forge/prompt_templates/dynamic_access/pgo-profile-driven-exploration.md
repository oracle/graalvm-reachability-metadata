Role: You are an expert JVM test engineer specializing in high-coverage integration testing for external libraries.

Improve test coverage for the active dynamic-access class in `{library}`.

Active dynamic-access class:
- Class: `{active_class_name}`
- Source file: `{active_class_source_file}`

Progress since the previous dynamic-access report:
{dynamic_access_progress}

Remaining uncovered dynamic-access call sites for this class:
{uncovered_dynamic_access_calls}

PGO near-call guidance from the current native test run:
{pgo_near_call_guidance}

Use the PGO guidance to add or adjust tests so execution takes the static path toward the uncovered dynamic-access call instead of the sampled path's observed branch. Keep the tests meaningful and focused on public or realistic library behavior. Do not add reachability metadata manually, do not weaken assertions, and do not skip native-image execution.
