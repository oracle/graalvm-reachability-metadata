# AR-agent-api: Forge agent API and backend adapters

Forge treats an agent as a replaceable editing backend behind a small Python
interface, preserving the boundary in §AR-forge-strategy-agent-boundary.
§FS-forge-agent-runtime-selection requires every adapter to expose the same
editing boundary.
Workflow engines decide what work to do next; agents only send prompts,
maintain or clear conversation context, report token usage, and run
agent-visible test commands, with bundles wiring the chosen backend into a
strategy (§FS-forge-predefined-strategy-contract).

## 1. Agent API

[`ai_workflows/agents/agent.py`](../../ai_workflows/agents/agent.py) defines the
`Agent` base class and registry. Concrete backends register with
`@Agent.register("<name>")`, and strategy loading resolves the configured
backend by the strategy's `agent` field (see
§FS-forge-predefined-strategy-contract).

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
test-command bridge, then
be selected through strategy data (§AR-forge-workflow-strategy-config) instead
of changing workflow drivers or workflow engines.

## 2. Backend implementations

The registry exposes `claude-code`, `pi`, `codex`, and `opencode`. Each adapter
starts its CLI unattended, with the tools that backend provides, so a repair
step can reproduce the failure it was given rather than editing blind.

`source_context.url_fetch_agent_command` builds the command string for URL-field
discovery, which Gradle invokes directly instead of through an adapter.

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
response to the workflow engine. Durable prompt, response, session, and failure
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
workflow engine chooses the gate (§AR-forge-workflow-engine), deterministic
utilities run it, and the agent receives diagnostics for the next edit cycle,
keeping the strategy/agent boundary intact (§AR-forge-strategy-agent-boundary).

## 3. Runtime roles and defaults

The three roles are configured from different places. `FORGE_ANALYSIS_AGENT` /
`FORGE_ANALYSIS_FAMILY` / `FORGE_ANALYSIS_MODEL` / `FORGE_ANALYSIS_PROVIDER`
select recovery, style, native-test, and post-generation work; the default is
Codex with `gpt-5.6-luna` and high reasoning, and Claude Code answers to its
`sonnet` model alias. Published-PR review is a specialized analysis turn that
selects Pi, the review model, provider, and thinking level through a per-turn
role environment. `FORGE_SETUP_*` selects artifact-URL discovery and the
library-preparation preflight. The roles do not read each other: what a role
leaves unset comes from the shared defaults, so retuning one never moves the
other. The do-work loop exports these values unchanged across its self-update
boundary (§AR-do-work-loop).

Every backend takes a reasoning effort, in its own spelling: Codex
`-c reasoning.effort`, Claude Code `--effort`, Pi `--thinking`, and OpenCode a
`reasoningEffort` model option rather than a flag. A role's resolved
`thinking_level` reaches all four, through the adapter and through the URL
discovery command string alike.

`get_analysis_agent()` and `get_setup_agent()` resolve a role to an
`AgentSelection` without running anything. A step that hands its prompt to an
adapter calls `analysis_agent_run(...)` or `setup_agent_run(...)` instead; the
getters exist for the two Gradle tasks, which own their own process and take
the role as a command string built by `source_context.url_fetch_agent_command`.

The test role has no runtime selection. A predefined strategy names the
`agent`, `model`, and `provider` it was written and measured against. The
strategy loader may replace only its machine-local `agent-command` through
`FORGE_TEST_AGENT_ALIAS`, so a strategy name still denotes the agent behind the
numbers recorded under it.

Every analysis step, including published-PR assessment, calls one function:
`agent_runtime.analysis_agent_run(working_dir, context, ...)`. The caller
assembles the context, while role resolution, adapter construction, durable
logging, result handling, and token accounting stay inside the shared runtime.
Ordinary call sites inherit the worker selection. PR review supplies its
dedicated Pi selection through the per-turn environment but never instantiates
or invokes Pi directly. The test role never passes through `agent_runtime`: a
driver's `init_agent` resolves the bundle's backend through `Agent.get_class`
and hands the instance to the workflow engine, which owns it for the
conversation.

`agent_process_environment` strips GitHub tokens and redirects the CLI config
for ordinary agent turns. Published-PR review sets one private, consumed-once
environment switch so its Pi adapter retains the already authenticated,
non-interactive `gh` session required by §FS-automated-pr-review; no other
analysis or setup caller sets that switch (§FS-forge-agent-runtime-selection).

Pi and OpenCode reach a model through a named **provider**: the analysis role
takes it from `--analysis-provider` or `FORGE_ANALYSIS_PROVIDER` and defaults to
`openai-codex`. The test role takes the bundle's own `provider` when it names
one, and the strategy loader fills in the same default when it does not, so the
value lives in one place rather than in every Pi entry. Codex and Claude Code authenticate their provider inside the CLI
itself, so a configured value is dropped rather than passed to a flag they do
not have. The same resolved provider reaches the adapter and the deterministic
authentication probe, so the host gate checks the provider the run will use.

Each role carries an adapter family and an executable name. The family selects
the registered adapter and its protocol; the executable name is passed to
that adapter, so machine-local aliases such as `cdx` are valid — the analysis
role through `FORGE_ANALYSIS_AGENT`, the test role through
`--test-agent-alias` or `FORGE_TEST_AGENT_ALIAS`. `FORGE_AGENT_FAMILY` is
retained as an analysis fallback for older workers.

## 4. Why Pi Is The Default Lightweight Backend

Forge prefers Pi for strategy profiles where it is sufficient because the
project goal is not maximum model strength on every turn; it is fast, reliable,
coverage-positive automation that uses the least tokens needed for the task.
The Forge direction (§GOAL-forge-direction) explicitly calls for runs to be as
cheap as practical, to prefer lightweight agents such as Pi when sufficient,
and to reset or clear sessions between independent steps so stale context does
not consume tokens.

Pi's RPC mode fits that goal operationally:

- strategy data can select Pi without changing workflow code;
- per-turn session stats feed the same token and cost metrics written by the
  [metrics utilities](../../utility_scripts/metrics_writer.py);
- `clear_context` makes cheap independent runs explicit;
- logs preserve enough evidence for maintainers or later Forge runs without
replaying a large conversation;
- provider and model flags keep the backend swappable while the Forge workflow
  contract stays stable.

Codex and other heavier agents remain useful for recovery paths or tasks that
need stronger autonomous repair, but Pi is the economical default when the
workflow can stay inside the narrow agent API and deterministic Gradle feedback
loop, matching §GOAL-forge-direction.
