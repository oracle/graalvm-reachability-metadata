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
- **A request carries facts, never hints.** It says what failed and shows the
  output; what to do follows from the rules in the instruction, so no line of a
  request can contradict them.
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
issue body when there is one. On the three `fails-*` labels it also carries
the unsupportable-version bar (§FS-unsupportable-version-diagnosis.1); no
other label sees it.

## 3. By state

| State | Fixed instruction carries | Handoff carries |
| --- | --- | --- |
| `setup` | how to find the four URLs, declare companion libraries, choose Docker images, and record a verdict when `run.md` names the bar | none; `run.md` only |
| `cover` | the step order, how a test reaches a call site, and the test rules | the mode and the exact targets |
| `fix` | the general rules for scope, tests, metadata, and Native Image | failing commands, the commands they kept from running, output, editable files, return code; for `audit`, the audit as its task |

The `fix` rules hold for every failure, which is why they live in the
instruction: scope (§root/FS-contribution-contract.5.2,
§root/FS-contribution-contract.5.3), the test contract
(§root/FS-test-contract.1, §root/FS-test-contract.2, §root/FS-test-contract.3),
metadata (§root/FS-test-contract.2.7, §root/FS-metadata.2,
§root/FS-contribution-contract.2), and Native Image
(§root/FS-test-contract.4, §root/FS-test-contract.2.7.1,
§root/FS-test-contract.2.7.2, §root/FS-contribution-contract.5.4). A new kind
of failure needs no prompt change, neither a rule block nor a state: its output
is the request.

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
and never a hint of what to do: the coordinate, the return code, the failing
commands, the commands a failure kept from running, the last 200 lines of each
command's output, and the files the agent may edit; a `native` request that
follows its own fix adds what that fix reported (§AR-forge-workflow-pipeline.6).
No table of known errors reads the output for the agent: a hint chosen by
matching text can misread it, and the rules that decide the fix already sit in
the instruction. A program check names itself in its output, so a reviewer gate
(§FS-local-ci-equivalent-verification.1.1) shows, under the gate command, a
line such as `diff-scope gate: outside the closed file set: a, b`.

The instruction orders the work: read the request and the files it names, make
every change with no Gradle run, rerun the failing commands and then the ones
they kept from running until all pass or nothing inside the rules helps, and
only then report `fixed` or `cannot-fix`.
The audit's request (§FS-local-branch-review.2) is the same shape with no
failing output: every command passes, and the audit is its task.
