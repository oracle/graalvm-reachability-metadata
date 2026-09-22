Task:
You are in a Gradle test project for the Java library `{library}`.
The library jar and its dependencies are already on the test classpath.

Improve the library's code coverage by writing new tests or extending the
ones you already wrote in earlier iterations.

- Write tests under: `{coverage_suite_test_source_root}`
- Run the tests: `{test_command}`
- Coverage report (read-only): `{jacoco_report_path}`
- Current coverage: {coverage_percent}% ({covered_methods}/{all_methods} library methods)

The coverage report is regenerated for you between iterations; do not run or
modify the coverage tooling yourself. §AR-code-coverage-improvement.3

Rules (test contract: §root/FS-test-contract):
- Write tests only under the coverage suite source root above — never in the
  coordinate's regular `src/test` sources, which are read-only context for
  this task. §AR-code-coverage-improvement.2
- Never put a test or helper in one of the library's own packages to reach
  package-private or internal code; use the test project's own namespace.
  §root/FS-test-contract.2.2
- Top-level test classes are `public` and follow idiomatic test-language
  conventions. §root/FS-test-contract.1.2
- Drive real library behavior through the public API and assert its observable
  results; no scaffold-only or trivial tests. §root/FS-test-contract.1.3
- No test-side reflection or serialization shortcuts unless the public API
  naturally requires them. §root/FS-test-contract.2.1
- No stubs, fakes, or shadow classes standing in for real library packages.
  §root/FS-test-contract.2.3
- Use only the API of the provided library version, and never hardcode artifact
  versions in test code. §root/FS-test-contract.1.4 §root/FS-test-contract.2.5
- Do not edit reachability metadata or Native Image config files.
  §root/FS-test-contract.2.7
- Update the coverage suite's `build.gradle` only when a missing dependency is
  required to exercise a public API path. §root/FS-test-contract.2.9
- All tests must pass before you finish.
