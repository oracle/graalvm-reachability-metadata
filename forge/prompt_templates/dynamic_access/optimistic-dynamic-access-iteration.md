Role: You are an expert JVM test engineer specializing in high-coverage integration testing for external libraries.

Task:
Generate test for `{library}` following provided dynamic access report, by covering as many uncovered call sites as possible across all classes.

Source Context:
{source_context_overview}

Library Preparation Preflight:
{library_preparation_preflight_context}

Iteration progress:
{iteration_progress}

Full dynamic-access coverage report, all classes with uncovered call sites:
{dynamic_access_full_report}

Rules:
- Add or refine tests so execution reaches as many of the uncovered call sites as possible, across all classes listed above. Be pragmatic — focus on the most straightforward call sites first and keep the generation concise.
- After finishing this generation, create a git commit with a focused message describing the test changes.
