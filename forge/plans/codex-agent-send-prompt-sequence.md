# CodexAgent `send_prompt()` Sequence

This diagram shows the current implementation.

- the first prompt uses `codex exec --json`
- follow-up prompts use `codex exec resume --json <session_id> <prompt>`
- `stdout` carries the session id, usage, and final assistant message

```mermaid
sequenceDiagram
    participant Caller
    participant CodexAgent
    participant CodexCLI as codex exec / exec resume --json

    Caller->>CodexAgent: send_prompt(prompt)
    CodexAgent->>CodexAgent: _build_effective_prompt(prompt)
    alt no session_id yet
        CodexAgent->>CodexCLI: codex exec --json <prompt>
    else existing session_id
        CodexAgent->>CodexCLI: codex exec resume --json <session_id> <prompt>
    end
    CodexCLI-->>CodexAgent: stdout JSONL event stream
    CodexAgent->>CodexAgent: _extract_session_id(stdout)
    CodexAgent->>CodexAgent: _extract_last_message(stdout)
    CodexAgent->>CodexAgent: _record_token_usage(stdout JSONL)
    CodexAgent->>CodexAgent: append interaction log
    CodexAgent-->>Caller: final assistant message
```

## The weird part

The only non-obvious part is that one JSON stream contains several concerns:

1. `stdout`
   - session id via `thread.started`
   - assistant reply via `item.completed`
   - usage via `turn.completed`

So `send_prompt()` has to parse JSONL rather than treat `stdout` as plain text.
