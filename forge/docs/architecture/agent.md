# AR-agent-api: Forge offline agent API and backend adapters

Forge treats an agent as a replaceable editing backend behind a small Python
interface, preserving the boundary in §AR-forge-strategy-agent-boundary.
§FS-forge-agent-runtime-selection requires every adapter to expose the same
offline editing boundary.
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
starts its CLI with only offline repository read/write/search tools. Web,
GitHub, MCP, extension, delegated-agent, and network-capable shell tools are denied. Deterministic utilities
run tests and fetch external context before the prompt.

`source_context.url_fetch_agent_command` is the one bounded exception: URL-field
discovery enables the selected analysis backend's fetch/search capability while
keeping GitHub credentials and general integrations unavailable. Normal turns
always use the offline adapter command.

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

Runtime configuration names two roles rather than encoding a product in a
workflow. `FORGE_ANALYSIS_AGENT` / `FORGE_ANALYSIS_MODEL` select recovery and
review work, while `FORGE_TEST_AGENT` / `FORGE_TEST_MODEL` override the
predefined strategy's test-generation backend and model. The analysis defaults
to Codex with `gpt-5.6-luna` and high reasoning (`xhigh` for pull-request
review); Claude Code defaults to its `sonnet` model alias. A strategy remains the test-role default when no override
is supplied. The do-work loop exports these values
unchanged across its self-update boundary (§DW-do-work-loop).

`FORGE_AGENT_FAMILY` is an optional Codex-launcher discovery input shared by
both roles. The runtime asks the compatible launcher for its installation
diagnostics, extracts the raw executable path, and uses the raw executable for
Codex turns and thread control. This preserves local-build selection without
letting wrapper arguments override Forge's offline Codex configuration.

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
