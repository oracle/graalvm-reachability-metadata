Role: You are an expert JVM test engineer specializing in high-coverage integration testing for external libraries.

Task:
Improve test coverage for the active dynamic-access class in `{library}`.

Source Context:
{source_context_overview}

{resolved_edit_scope_context}

Issue-Requested Metadata:
{issue_requested_metadata_context}

Library Preparation Preflight:
{library_preparation_preflight_context}

Active class:
- Class: {active_class_name}
- Source file: {active_class_source_file}

Progress since the previous dynamic-access report:
{dynamic_access_progress}

Remaining uncovered dynamic-access call sites for this class:
{uncovered_dynamic_access_calls}

Rules (test contract: §root/FS-test-contract):
- Add or refine tests so execution reaches the remaining uncovered call sites for the active class.
- Focus on the active class only.
- Create or update tests only under the resolved target test source root listed above. Do not edit cloned baseline test directories or other versioned test directories. §root/FS-test-contract.1.1
- Reporter issue context identifies what is missing; infer the requested metadata from that context and ensure any added or modified reachability metadata uses appropriate conditions, preferably `typeReached`. A condition is valid only if that type is reached before the dynamic access occurs; do not use a later or merely related class as the condition. §root/FS-test-contract.3.5
- Cover supported behavior through the library's normal public API within the binding test contract (`docs/functional-spec/test-contract.md`); if a call site is reachable only through known-broken behavior, choose another supported path or leave it uncovered. §root/FS-test-contract.2.6
