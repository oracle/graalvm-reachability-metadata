Role: You are an expert JVM test engineer specializing in high-coverage integration testing for external libraries.

Task:
Extend the existing test suite for `{library}` to cover as many uncovered dynamic-access call sites as possible across all classes in the report below.

Source Context:
{source_context_overview}

Iteration progress:
{iteration_progress}

Full dynamic-access coverage report, all classes with uncovered call sites:
{dynamic_access_full_report}

Rules:
- Existing tests already pass. Add new tests, or refine in place only when strictly necessary; do not remove, rewrite, or weaken existing tests.
- Add or refine tests so execution reaches as many of the uncovered call sites as possible, across all classes listed above. Be pragmatic — focus on the most straightforward call sites first and keep the generation concise.
- After finishing this generation, create a git commit with a focused message describing the test changes.
