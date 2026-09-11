Task:
- Analyze the existing test methods in the provided file to determine the current coverage.
- Identify a new, functional feature of the library `{library}` that is not yet covered by the existing tests, and write tests for it.

Issue-Requested Metadata:
{issue_requested_metadata_context}

Rules (test contract: §root/FS-test-contract):
- The binding test contract `docs/functional-spec/test-contract.md` applies to every test you write.
- Do not change any test logic that is already done.
- Do not re-test classes or logic flows already present in the file.
- Keep the test in `{test_language_display_name}` under `src/test/{test_source_dir_name}`.
- Don't duplicate tested features.
- Reporter issue context identifies what is missing; infer the relevant public API paths from that context and cover them in tests. §root/FS-test-contract.1.5
