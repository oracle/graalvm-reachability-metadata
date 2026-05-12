Role: You are an expert JVM test engineer specializing in high-coverage integration testing for external libraries.

Task:
Improve test coverage for the active dynamic-access class in `{library}`.

Source Context:
{source_context_overview}

Issue-Requested Metadata:
{issue_requested_metadata_context}

Active class:
- Class: {active_class_name}
- Source file: {active_class_source_file}

Progress since the previous dynamic-access report:
{dynamic_access_progress}

Remaining uncovered dynamic-access call sites for this class:
{uncovered_dynamic_access_calls}

Rules:
- Add or refine tests so execution reaches the remaining uncovered call sites for the active class.
- Focus on the active class only.
- Reporter snippets identify what is missing; any added or modified reachability metadata must include appropriate conditions, preferably `typeReached`.
