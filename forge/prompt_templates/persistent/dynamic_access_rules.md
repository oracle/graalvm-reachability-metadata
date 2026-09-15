Rules (test contract: §root/FS-test-contract):
- The binding test contract for every test you create or edit is
  `docs/functional-spec/test-contract.md` in this repository. Read it in full
  before your first edit and follow all of it: what a test must have
  (§root/FS-test-contract.1), what it must not do (§root/FS-test-contract.2),
  what it should do (§root/FS-test-contract.3), and the non-negotiable Native
  Image execution contract (§root/FS-test-contract.4).
- When a build failure or reviewer finding cites a contract section, re-read
  that section before fixing; never work from memory of the contract.
- Add or refine tests so execution reaches uncovered dynamic-access call
  sites, keeping one dedicated `{test_language_display_name}` test file under
  `src/test/{test_source_dir_name}` per dynamic-access class and the `$`-free
  naming scheme of §root/FS-test-contract.1.8.
- Do not compile or run tests yourself. The workflow will do that externally.
  §AR-forge-strategy-agent-boundary
