# AR-code-coverage-prompts: Agent prompt composition

Every agent turn of the code coverage workflow (§AR-code-coverage-improvement)
receives one prompt that Rhei assembles from several layers, and each layer has
one scope. The workflow-wide part of a prompt carries only Rhei's own
scaffolding, the rules that hold for every agent state, and the repository
rules. Everything else belongs to the task or state it serves: a cover state's
prompt is its own instructions plus the report measurement rendered for it.
Rhei pastes a workflow-wide layer into every turn of every pass, so a sentence
there that only some agents can act on is paid for on every pass
(§GOAL-minimize-generation-cost) and pulls the agent's attention off the
targets it was handed (§GOAL-maximize-library-coverage).

## 1. Prompt layers

Rhei builds the prompt in a fixed order; the template authors four of the
layers and Rhei generates the rest.

| Layer | Source | Reaches | Carries |
| --- | --- | --- | --- |
| Task header, `## Position`, `## Prior Task Results`, `## Plan History`, `## Previous Visits`, `## Rhei Commands` | Rhei | every agent turn | Plan state and Rhei's own rules; not authored by the template |
| `### Rhei Context` | every `##` section of the template's `index.rhei.md`, verbatim | every agent turn of every task | The workflow-wide `## Rules` and nothing else |
| `## Instructions` | the state's `instructions` in `states.yaml` | that state's turns | What the state does and every rule specific to it |
| `## Task Content` | the task body in `tasks/`; omitted when empty | every agent state of that task | Facts each of those states acts on; empty for a loop task |
| `## Handoff from <state>` | the prompt measurement or dispatch rendered (§AR-code-coverage-prompts.3) | the cover state it is handed to | The targets and the evidence for each |
| Repository rules | `AGENTS.md` in the worktree, read by the agent runtime | every agent turn | Repository conventions; never restated by the template |

## 2. Scope rule

A sentence lives in the narrowest layer whose every reader acts on it.

### 2.1 Workflow-wide rules

`index.rhei.md` holds one `## Rules` section with only what every agent state
must do: run Gradle in the foreground because the turn is headless, write the
state's output before the final message, resolve artifact paths against the
workspace root, and leave the plan files alone. A rule that holds for some
states but not all belongs in those states' instructions. These rules carry no
coverage guidance, so the naive arm still receives none
(§AR-code-coverage-benchmarking.3).

### 2.2 No workflow description in a prompt

How the workflow runs — its phases, budgets, stop rule, verification, and
finalization — is for people. It lives in the template's `README.md`, which
Rhei does not paste, and in these architecture documents. An agent cannot act
on a description of the pipeline around it, so the template keeps no
`Overview`, `Source`, or `Verification` section in `index.rhei.md`.

### 2.3 Loop task bodies are empty

A loop task's agent states are its cover and fix states, and they share the
task body. What the measurement program runs, where its report lives, how the
target universe and target rotation are defined, and how the loop moves are
the measure state's business: its program implements them, the fix state's
instructions name every step it can repair, and §AR-code-coverage-improvement.4
and §AR-code-coverage-deep-navigation document them. The API, deep, and naive
loop tasks therefore carry no body, and Rhei omits their `## Task Content`.

### 2.4 State rules are concrete

A state's rules govern the work that state does: which targets to attempt and
how to reach them, how to read the handed-over report, where tests go and how
they are laid out, and which command checks them. A rule that repeats another
rule, or forbids something the state has no means to do, is dropped: the API
and deep cover states keep "never touch the regular `src/test` sources" and
drop "do not weaken metadata-generation tests". "Do not measure coverage
yourself" is not such a repeat: "`codeCoverageTest` is the only Gradle task you
run" leaves plain `java` free, and with only that rule a commons-compress deep
session wrote its own JaCoCo reader to check its targets. The cover states keep
it and name JaCoCo output explicitly. The naive cover state keeps its two rules
unchanged, because its prompt is the benchmark's control
(§AR-code-coverage-benchmarking.3).

### 2.5 The plan is Rhei's

An agent never edits `index.rhei.md`, `states.yaml`, or a task file. A note
written into a task body reaches every later turn of that task as
`## Task Content`: on the h2 2.1.210 benchmark a pass-6 summary an agent
logged into the deep task was still pasted into the prompt of pass 9. A
state's output file is the agent's only report.

## 3. Measurement prompts arrive as handoffs

The API and deep cover prompts are a Rhei handoff the cover state inherits as
required, so the agent receives the whole document in its message, not a path
to read: an agent told to read a long file reads part of it, and every target
it never sees is coverage the pass cannot add. `api-measure` writes the API
prompt; in the deep phase the session dispatcher writes one group session at a
time (§AR-code-coverage-deep-navigation.4). The cover state's instructions name
that handoff as the target list, and the file it came from stays on disk for
audit. The naive arm's prompt is a short rendered template the cover agent
reads by path (§AR-code-coverage-benchmarking.3).

## 4. The deep-cover prompt

A deep-cover turn therefore reads, in order: Rhei's task header and position,
the workflow-wide rules, its instructions, the session handed over by
`deep-dispatch`, and Rhei's history and commands. The instructions say how to
reach each listed path through its public entry and never call an internal
target directly, how to read `Observed`, `Branches not taken`, and
`never dispatched here` (§AR-code-coverage-deep-navigation.3), where the tests
go and how they are grouped per subsystem, the file header and ASCII rules
finalization enforces, the write-then-`codeCoverageTest` loop inside the
45-minute session, and what the session's output records.
