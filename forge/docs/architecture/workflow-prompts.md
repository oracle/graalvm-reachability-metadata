# AR-forge-workflow-prompts: Forge workflow prompts

Each of the three agent states of §AR-forge-workflow-pipeline has one fixed
instruction, and everything that varies between runs or passes is a handoff a
program writes, so an instruction change never depends on a run and the next
prompt is always derived from a measurement (§FS-forge-agent-runtime-selection,
§root/PRCPL-prefer-algorithmic). Every agent prompt is composed the same way:

```text
fixed instruction (template)  +  run.md (prepare)  +  handoff (sender program)
```

## 1. Rules of the design

- **A fixed instruction never names a coordinate, a count, or a file.** Those
  come from `run.md` or the handoff.
- **Rules are cited, not restated.** Each rule is one line with its spec ID, so
  the spec stays the single source (§root/FS-test-contract).
- **A brief is one line of what to do, never a rule.** Rules sit in the
  instruction, so a brief cannot contradict them.
- **Every agent state orders its work.** Read, then edit everything, then run
  Gradle, then report. No Gradle task runs while the agent is still editing, so
  each check runs on a finished change instead of once per file.
- **Only programs write handoffs.** No agent output feeds another agent's
  prompt.

## 2. The run note

`prepare` writes `runtime/forge/run.md` once and every agent state reads it
first: the coordinate, the issue and its label, the route and its explore goal,
the absolute test-project and metadata paths, the allowed packages, the main
sources directory, the URL discovery command, and the path of the reporter's
issue body when there is one.

## 3. By state

| State | Fixed instruction carries | Handoff carries |
| --- | --- | --- |
| `setup` | how to find the four URLs, declare companion libraries, and choose Docker images | none; `run.md` only |
| `cover` | the step order, how a test reaches a call site, and the test rules | the mode and the exact targets |
| `fix` | the general rules for scope, tests, metadata, and Native Image | failing commands, output, briefs, editable files, return code |

The `fix` rules hold for every failure, which is why they live in the
instruction: scope (§root/FS-contribution-contract.5.2,
§root/FS-contribution-contract.5.3), the test contract
(§root/FS-test-contract.1, §root/FS-test-contract.2, §root/FS-test-contract.3),
metadata (§root/FS-test-contract.2.7, §root/FS-metadata.2), and Native Image
(§root/FS-test-contract.4, §root/FS-test-contract.2.7.1,
§root/FS-test-contract.2.7.2, §root/FS-contribution-contract.5.4). A new check
needs a brief row at most, never a new rule block or state.

## 4. Cover prompts

`analyze` writes the first and `measure` every later one, rendered from
`prompt_templates/dynamic_access/`:

- **Bulk.** Every class with uncovered calls, with its call sites; the first
  prompt also lists the reporter's request as a required target.
- **Class.** One class's uncovered call sites and its attempt number; when the
  previous attempt gained nothing, the prompt says so and names the tests that
  were reset.
- **Basic.** No dynamic-access target: tests of the main public API in the
  allowed packages, and the reporter's request when there is one.

## 5. The fix request

`handoff.py` writes it for every sender. It carries the facts of this failure
and nothing else: the return code, the failing commands, a one-line brief per
recognized error, the last 200 lines of each command's output, and the files
the agent may edit. Each output is matched against a small table, and an
unmatched failure gets the default brief:

| Error in the output | Brief |
| --- | --- |
| test compilation error | move the tests to the API this version has, keeping what each asserts |
| JVM test failure | adapt the test to this version's behavior; drop a test only for removed API |
| `checkMetadataFiles`: condition outside the allowed packages | regenerate through the harness tasks; route a type another artifact owns to that owner |
| `checkMetadataFiles`: test-only type | run `splitTestOnlyMetadata` |
| `nativeTest`: missing registration | add the entry the error names to the shipped metadata, with a `typeReached` condition in the allowed packages |
| `nativeTest`: runtime class definition or bytecode generation | reshape the test to avoid it, or delete that test |
| a native build or run names a flag, such as a URL protocol | add it if it is on the allowed list, in its narrowest form |
| `spotlessCheck` or `checkstyle` | run `spotlessApply`, then fix what `checkstyle` reports |
| anything else | make the command pass |

The instruction orders the work: read the request and the files it names, make
every change with no Gradle run, rerun the failing commands until they pass or
nothing inside the rules helps, and only then report `fixed` or `cannot-fix`.
