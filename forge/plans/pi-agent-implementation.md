# Pi Agent Implementation Plan

## Context

`Agent` base class with a registry pattern. We want to add **Pi** as a new backend
so workflows can run strategies with `agent: "pi"`.

Pi exposes a JSONL RPC interface over stdin/stdout (`pi --mode rpc ...`),
documented in `plans/pi-rpc-demo-script-interface.md`, with a reference client in
`utility_scripts/pi_rpc_demo.py`. The goal is to wrap that protocol behind the
existing `Agent` interface, mirroring the split used by Codex:
`CodexAgent` (agent interface) + `CodexAppServerClient` (RPC transport).

## Design

Two new files, following the Codex layout:

1. **`ai_workflows/agents/pi_rpc_client.py`** — `PiRpcClient`
   A thin wrapper around a `pi --mode rpc` subprocess. Owns JSONL framing
   and the three RPC commands (`prompt`, `get_state`, `get_session_stats`).
   Analogue of `CodexAppServerClient`.

2. **`ai_workflows/agents/pi_agent.py`** — `PiAgent(Agent)`
   Registered via `@Agent.register("pi")`. Implements the abstract interface
   from `ai_workflows/agents/agent.py` using `PiRpcClient` for transport.

### PiRpcClient responsibilities

`PiRpcClient` is a stateless helper: each method launches a short-lived
`pi --mode rpc ...` subprocess, runs the exchange, and tears it down. It
does **not** keep a long-running Pi process between calls. Its job is to
hide the CLI flags, JSONL framing, and command/event matching.

Constructor params (defaults used by every launch):
`pi_command="pi"`, `session_dir: str | None`, `provider: str | None`,
`model: str | None`, `working_dir: str | None`, `timeout: int`.

Public methods (each launches its own subprocess, runs the exchange, closes):

- `run_prompt(prompt: str, session: str | None) -> PromptResult`
  - Launches `pi --mode rpc` with `--session <session>` when `session` is
    given (plain continuation; no fork). When `None`, starts a fresh
    session.
  - Sends `{"type":"prompt", ...}`, accumulates
    `message_update.assistantMessageEvent.text_delta` chunks into a buffer,
    stops on `agent_end`, raises on `response.success=false` or unexpected
    EOF.
  - Before closing, calls `get_state` and `get_session_stats` over the same
    subprocess (the spec recommends this right after `agent_end`).
  - Returns a small dataclass:
    `PromptResult(text, session_file, session_stats)` where `session_file`
    is the `sessionFile` string from `get_state`. We intentionally ignore
    `sessionId` — the path is the durable handle.

- `fork(session: str, prompt: str) -> PromptResult`
  - Same as `run_prompt` but launches with `--fork <session>` instead of
    `--session`. Runs the prompt, fetches state + stats, returns the **new**
    `session_file` (the forked session path) plus response text and stats.

Internally both methods share a `_run(command_flags, prompt)` helper that
does Popen + JSONL loop + timeout + terminate, mirroring
`utility_scripts/pi_rpc_demo.py` but with timeout enforcement and
guaranteed cleanup in a `try/finally`. This keeps protocol knowledge out of
`PiAgent`.

### PiAgent responsibilities

State:
- `_model_name`, `_provider` (optional), `_working_dir`, `_timeout`
- `_rpc_client: PiRpcClient` — constructed once in `__init__`, reused for
  every turn.
- `_session_path: str | None` — durable `sessionFile` path returned by Pi.
  This is the **only** session handle we persist; we do not store
  `sessionId`.
- `_total_tokens_sent`, `_total_tokens_received`, `_cached_input_tokens_used`
- `_prev_input`, `_prev_output`, `_prev_cache` — whole-session token
  snapshots for delta computation.

Interface implementation:

- `send_prompt(prompt)`:
  1. `result = self._rpc_client.run_prompt(prompt, session=self._session_path)`.
     On the very first turn `self._session_path` is `None`, so Pi creates a
     fresh session.
  2. Set `self._session_path = result.session_file`.
  3. Update token counters from `result.session_stats` using the delta
     strategy described below.
  4. Write a turn log under `logs/` keyed by the session path basename
     (mirroring `CodexAgent._write_turn_log`).
  5. Return `result.text`.

- `fork(prompt)`:
  - Requires `self._session_path` to be set; otherwise `RuntimeError`.
  - `result = self._rpc_client.fork(self._session_path, prompt)`.
  - Build a child via `_clone()`, set `child._session_path =
    result.session_file` (the newly forked path), copy cumulative token
    counters from parent, reset child's `_prev_*` to the stats of the new
    forked session so future deltas are relative to the fork point, return
    the child.

- `compact_fork(prompt)`:
  - Pi RPC has no documented compact operation. Implement as an alias to
    `fork(prompt)` with a code comment noting the fallback (same pragmatic

- `clear_context()`:
  - Set `self._session_path = None` so the next `send_prompt` starts a brand
    new Pi session.

- `run_test_command(test_cmd)`:
  - Identical to `CodexAgent.run_test_command` — `subprocess.run` with
    `shell=True`, `cwd=self._working_dir`, capture combined stdout/stderr.

- Token properties: return the running counters.

### Token accounting

`get_session_stats` returns **whole-session** totals, not per-turn deltas.
Strategy: after each turn compute

```
delta_in    = stats.tokens.input     - prev_input
delta_out   = stats.tokens.output    - prev_output
delta_cache = stats.tokens.cacheRead - prev_cache
```

and add deltas to the cumulative counters. Track `prev_*` on the instance.
After `fork()` the child's `prev_*` is initialised from the forked
session's current stats so future deltas are relative to the fork point;
cumulative counters are inherited from the parent.

### Registration

Add to `ai_workflows/agents/__init__.py`:

```python
from ai_workflows.agents.pi_agent import PiAgent
```

so the `@Agent.register("pi")` decorator fires at import time and workflows
can request `agent: "pi"` via `Agent.get_class("pi")`.

## Files

Create:
- `ai_workflows/agents/pi_rpc_client.py`
- `ai_workflows/agents/pi_agent.py`

Modify:
- `ai_workflows/agents/__init__.py` — add PiAgent import.

Reference (read-only, for patterns):
- `ai_workflows/agents/agent.py` — abstract interface
- `ai_workflows/agents/codex_agent.py` — log/token/fork patterns, `_clone`,
  `run_test_command`
- `ai_workflows/agents/codex_app_server.py` — JSONL subprocess client pattern
- `utility_scripts/pi_rpc_demo.py` — protocol reference (do not import from
  `utility_scripts/`; re-implement in `ai_workflows/agents/pi_rpc_client.py`)
- `plans/pi-rpc-demo-script-interface.md` — authoritative protocol spec

## Verification

1. **Import sanity**: `python -c "from ai_workflows.agents import PiAgent;
   from ai_workflows.agents.agent import Agent; print(Agent.get_class('pi'))"`
   resolves to `PiAgent`.
2. **Smoke test against real Pi** (requires `pi` on PATH):
   ```python
   from ai_workflows.agents.pi_agent import PiAgent
   agent = PiAgent(model_name="<model>", working_dir=".")
   print(agent.send_prompt("Say hello in one word."))
   print(agent.total_tokens_sent, agent.total_tokens_received)
   print(agent.send_prompt("And in French?"))   # exercises session continuation
   child = agent.fork("Now in German.")         # exercises --fork
   child.clear_context()
   ```
   Expect: (a) response text printed, (b) monotonically increasing token
   counters, (c) the second prompt actually sees the earlier context, (d)
   `child._session_path` differs from `agent._session_path`.
3. **run_test_command**: `agent.run_test_command("echo ok")` returns `"ok\n"`.
4. **Workflow integration**: run an existing workflow (e.g.
   `add_new_library_support.py`) with a strategy whose `agent` key is `"pi"`
   and confirm it picks up `PiAgent` via the registry.
