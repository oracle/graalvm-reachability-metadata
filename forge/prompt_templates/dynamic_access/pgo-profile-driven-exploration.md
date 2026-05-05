Role: You are an expert JVM test engineer specializing in high-coverage integration testing for external libraries.

Improve test coverage for the active dynamic-access call in `{library}`.

Active dynamic-access target:
- Class: `{active_class_name}`
- Source file: `{active_class_source_file}`

Progress since the previous dynamic-access report:
{dynamic_access_progress}

Target uncovered dynamic-access call site:
{uncovered_dynamic_access_calls}

PGO near-call guidance from the current native test run:
{pgo_near_call_guidance}

Use the PGO guidance to add or adjust existing tests so execution takes the static path toward this one uncovered dynamic-access call instead of the sampled path's observed branch. Keep the tests meaningful and focused on public or realistic library behavior. Do not add reachability metadata manually, do not weaken assertions, do not scaffold new test modules, and do not skip native-image execution.
