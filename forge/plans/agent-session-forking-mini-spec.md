# Agent Forking Mini-Spec

## Goal

Support branching for agents without introducing a separate workflow-managed session-state model.

Each agent owns its own state. The abstraction only exposes three operations:

- `send_prompt(msg)`
- `fork(msg)`
- `compact_fork(msg)`

Default behavior must remain equivalent to current Codex-style usage: a fresh call with no inherited state unless branching is explicitly requested.

---

## Core API

```python
class Agent(ABC):
    def send_prompt(self, msg: str) -> str:
        ...

    def fork(self, msg: str) -> "Agent":
        ...

    def compact_fork(self, msg: str) -> "Agent":
        ...
```

---

## Semantics

### `send_prompt(msg)`

Send `msg` to the current agent instance.

Behavior:

- uses the agent's current state
- for stateless agents, this is effectively a fresh call every time
- for stateful agents, this continues the current conversation/session

### `fork(msg)`

Create a **new branch from the current agent state rather than starting from scratch**, then send `msg` to that new branch.

Behavior:

- child branch starts from the exact current state of the parent
- parent remains unchanged after the fork
- the returned agent is the new child branch

Use `fork(msg)` when you want to explore an alternative path from the same context.

### `compact_fork(msg)`

Create a **new branch from a compact summary of the current state**, then send `msg` to that new branch.

Behavior:

- child branch does not inherit the full prior state
- child starts from a summarized version of the current context
- parent remains unchanged
- the returned agent is the new compact child branch

Use `compact_fork(msg)` when you want branching, but the full current context is too large or too noisy.

---

## Behavior Matrix

| Method | Starts Fresh | Uses Full Current State | Uses Summarized State | Creates Branch |
|---|---:|---:|---:|---:|
| `send_prompt(msg)` | default for stateless agents | yes for stateful agents | no | no |
| `fork(msg)` | no | yes | no | yes |
| `compact_fork(msg)` | no | no | yes | yes |

---

## Agent-Type Interpretation

### Stateless agents

Stateless agents do not keep model-side memory between calls, so branching must be implemented by the agent adapter.

Expected interpretation:

- `send_prompt(msg)` starts from empty context
- `fork(msg)` sends `msg` together with the full reconstructed context of the current branch
- `compact_fork(msg)` sends `msg` together with a compact summary of the current branch

The branching behavior is real, but it is implemented by replaying or summarizing prior context.

### Stateful agents

Stateful agents may implement branching more directly.

Expected interpretation:

- `send_prompt(msg)` continues the active session
- `fork(msg)` creates a child session from the current one
- `compact_fork(msg)` creates a child session from a summarized copy of the current one

If the underlying tool does not support native forks, the adapter may emulate them using summaries or replay.

---

## Invariants

1. `send_prompt(msg)` does not branch.
2. `fork(msg)` creates an exact child branch from current state.
3. `compact_fork(msg)` creates a summarized child branch from current state.
4. Parent and child branches do not mutate each other after branching.
5. Branching is explicit. If a workflow does not call `fork(...)` or `compact_fork(...)`, no branch is created.
6. Stateless-agent default behavior stays fresh unless branching reconstructs prior context on purpose.

---

## Recommended Usage

```python
root = agent
root.send_prompt("Implement the initial scaffold.")

alt = root.fork("Try a different implementation strategy.")

small = root.compact_fork("Continue from a short summary and focus only on metadata fixes.")
```

---