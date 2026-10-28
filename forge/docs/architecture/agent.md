# AR-agent-api: Forge agent API and backend adapters

Forge treats an agent as a replaceable editing backend behind a small Python
interface, preserving the boundary in §AR-forge-strategy-agent-boundary.
§FS-forge-agent-runtime-selection requires every adapter to expose the same
editing boundary.
The `forge-metadata` run does not use these adapters: Rhei runs its agent
states (§AR-forge-workflow-pipeline.8). They serve the steps outside it that
still hand a prompt to an agent — the code coverage workflow's native trace gate
and local CI fixups (§AR-code-coverage-improvement). Callers decide what work to
do next; agents only send prompts, maintain or clear conversation context,
report token usage, and run agent-visible test commands.

## 1. Agent API

[`ai_workflows/agents/agent.py`](../../ai_workflows/agents/agent.py) defines the
`Agent` base class and registry. Concrete backends register with
`@Agent.register("<name>")`, and `agent_runtime` resolves the configured
backend by name (§FS-forge-agent-runtime-selection).

The interface is deliberately narrow:

| Method or property | Contract |
| --- | --- |
| `send_prompt(prompt)` | Send one prompt to the current agent session and return the agent's text response. |
| `fork(prompt)` | Branch from the current conversation and send the prompt in the child session. |
| `compact_fork(prompt)` | Branch with reduced context when the backend supports compaction. |
| `clear_context()` | Drop agent-side conversation state before an independent step or run. |
| `run_test_command(test_cmd)` | Execute a deterministic test command and return diagnostics in the form expected by the agent. |
| `graphify(source_dirs)` | Build optional graph context from read-only source directories. |
| `total_tokens_sent` / `total_tokens_received` / `cached_input_tokens_used` | Expose token accounting for run metrics and cost reporting. |

This API keeps workflow behavior out of backend adapters. A new backend should
implement the registry key, the prompt/session methods, token counters, and
test-command bridge, then be selected through the role variables of section 3
instead of changing a caller.

## 2. Backend implementations

The registry exposes `claude-code` and `pi`; Codex and OpenCode are not
supported (§FS-forge-agent-runtime-selection). Each adapter starts its CLI
unattended, with the tools that backend provides, so a repair step can reproduce
the failure it was given rather than editing blind.

[`PiAgent`](../../ai_workflows/agents/pi_agent.py) registers the `pi` backend and
drives Pi through [`PiRpcClient`](../../ai_workflows/agents/pi_rpc_client.py), a
thin subprocess wrapper around `pi --mode rpc`. The client starts Pi in the
workflow working directory, applies optional `--provider`, `--model`,
`--session-dir`, and persistent system-prompt flags, sends prompt JSON over
stdin, and reads structured RPC events from stdout until the agent reports the
turn is complete.

Each successful Pi turn returns a `PromptResult` with:

- the assembled assistant text from `message_update` deltas;
- the active Pi session file from `get_state`;
- token totals from `get_session_stats`;
- the RPC transcript used for durable debugging.

`PiAgent.send_prompt` continues the current session when one exists, updates the
session file after every turn, computes per-turn input, output, and cache-read
token deltas, writes a coordinate-scoped session log, and returns only the text
response to the caller. Durable prompt, response, session, and failure
logs are part of the Forge diagnostic contract defined in
§FS-durable-generation-logs.

`PiAgent.fork` uses Pi's `--fork <session>` support to create a child
conversation while preserving the parent counters as the baseline for the child.
`compact_fork` currently delegates to `fork` because Pi RPC does not expose a
documented compaction-aware fork operation. `clear_context` drops the stored
session path and token baselines so the next prompt starts as an independent Pi
session.

`PiAgent.run_test_command` delegates Gradle execution to the
[shared test runner](../../utility_scripts/gradle_test_runner.py) instead of
asking Pi to choose shell behavior. That preserves the architecture: the
caller chooses the gate, deterministic utilities run it, and the agent receives
diagnostics for the next edit cycle (§AR-forge-strategy-agent-boundary).

## 3. Runtime roles and defaults

Two roles are configured, each from its own variables. `FORGE_ANALYSIS_AGENT` /
`FORGE_ANALYSIS_FAMILY` / `FORGE_ANALYSIS_MODEL` / `FORGE_ANALYSIS_PROVIDER`
select recovery, style, and native-test work; `FORGE_SETUP_*` selects setup
work. The defaults are Pi with `gpt-5.6-luna` through the `openai-codex`
provider, and Claude Code answers to its `sonnet` model alias. The roles do not
read each other: what a role leaves unset comes from the shared defaults, so
retuning one never moves the other.

Each backend takes a reasoning effort in its own spelling: Claude Code
`--effort` and Pi `--thinking`. A role's resolved `thinking_level` reaches both
through the adapter.

`get_analysis_agent()` and `get_setup_agent()` resolve a role to an
`AgentSelection` without running anything; the host gate uses them to probe the
agent the run will use. A step that hands its prompt to an adapter calls
`analysis_agent_run(working_dir, context, ...)` or `setup_agent_run(...)`: the
caller assembles the context, because only the caller knows what failed, and
role resolution and agent construction happen inside. No call site selects a
backend, model, provider, or thinking level for itself.

Pi reaches a model through a named **provider**, taken from
`FORGE_<ROLE>_PROVIDER` and defaulting to `openai-codex`. Claude Code
authenticates its provider inside the CLI itself, so a configured value is
dropped rather than passed to a flag it does not have. The same resolved
provider reaches the adapter and the deterministic authentication probe, so the
host gate checks the provider the run will use.

Each role carries an adapter family and an executable name. The family selects
the registered adapter and its protocol; the executable name is passed to that
adapter, so machine-local aliases are valid through `FORGE_<ROLE>_AGENT`.
`FORGE_AGENT_FAMILY` is retained as a fallback for older workers.

## 4. Why Pi Is The Default Lightweight Backend

Forge prefers Pi where it is sufficient because the
project goal is not maximum model strength on every turn; it is fast, reliable,
coverage-positive automation that uses the least tokens needed for the task.
The Forge direction (§GOAL-forge-direction) explicitly calls for runs to be as
cheap as practical, to prefer lightweight agents such as Pi when sufficient,
and to reset or clear sessions between independent steps so stale context does
not consume tokens.

Pi's RPC mode fits that goal operationally:

- configuration can select Pi without changing workflow code;
- per-turn session stats feed the same token and cost metrics written by the
  [metrics utilities](../../utility_scripts/metrics_writer.py);
- `clear_context` makes cheap independent runs explicit;
- logs preserve enough evidence for maintainers or later Forge runs without
replaying a large conversation;
- provider and model flags keep the backend swappable while the Forge workflow
  contract stays stable.

Claude Code remains the choice where a step needs web search, such as setup,
but Pi is the economical default when the workflow can stay inside the narrow
agent API and deterministic Gradle feedback loop, matching
§GOAL-forge-direction.
