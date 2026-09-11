Workflow rules (test contract: §root/FS-test-contract):
- The binding test contract for every test you create or edit is
  `docs/functional-spec/test-contract.md` in this repository. Read it in full
  before your first edit and follow all of it, including the Native Image
  execution contract (§root/FS-test-contract.4) and the bounds on metadata and
  build configuration (§root/FS-test-contract.2.7).
- Modify only files the workflow made editable. §AR-forge-strategy-agent-boundary
- Do not compile, run, or verify tests yourself; the workflow runs validation
  externally. §AR-forge-strategy-agent-boundary
- Keep edits focused on the active library and requested workflow task.
  §root/FS-test-contract.2.9
