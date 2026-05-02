# Persistent Agent Instructions Plan

## Context

Forge currently sends durable workflow rules through the first normal agent prompt (`predefined_prompt`). In long-running sessions, agents can drift away from those rules because they are only part of conversation history and can be diluted by later prompts, tool output, and compaction.

The goal is to move stable, high-priority workflow rules into each agent's persistent instruction layer while keeping task-specific details in normal user prompts.

## Findings

### Pi

Pi RPC does not need a JSON RPC command for this. The persistent instruction layer can be configured before the RPC server starts:

```bash
pi --mode rpc --append-system-prompt "persistent rules..."
```

Relevant Pi behavior:

- `--append-system-prompt <text>` appends rules to Pi's default system prompt.

Recommended Pi mapping:

```text
persistent_instructions -> pi --mode rpc --append-system-prompt <persistent_instructions>
```

### Codex

Codex CLI does not expose the same `--append-system-prompt` flag, but it supports high-priority developer instructions through config overrides:

```bash
codex exec \
  -c 'developer_instructions="persistent rules..."' \
  'task prompt...'
```

A local inspection with `codex debug prompt-input` showed `developer_instructions` rendered as a model-visible `role: "developer"` message. Codex also loads `AGENTS.md` into developer context, so repo-wide rules already have a relatively high-priority layer.

Recommended Codex mapping:

```text
persistent_instructions -> codex exec -c 'developer_instructions="<persistent_instructions>"'
```

For Codex app-server lifecycle operations, pass the same value in the thread `config` object as `developer_instructions` if supported by the app-server config surface.

## Proposed Abstraction

Introduce an agent-neutral concept named `persistent_instructions`, not `system_prompt`, because each backend exposes a different instruction layer.

```text
persistent_instructions
  Workflow-wide durable rules that should outrank normal prompts.

normal prompt templates
  Task-specific and iteration-specific instructions.
```

The abstraction should be mapped by agent adapters:

| Agent | Mapping |
| --- | --- |
| Pi | `--append-system-prompt <persistent_instructions>` |
| Codex CLI | `-c 'developer_instructions="<persistent_instructions>"'` |
| Codex app-server | `config.developer_instructions = <persistent_instructions>` if accepted |

## Candidate Instruction Split

Move only **Rules** section from prompts to `persistent_instructions`, for example:

Keep task-specific details in normal prompts, for example:

- Target coordinates and version.
- Current dynamic-access class or uncovered call sites.
- Current Gradle failure output.
- Source-context paths.
- Iteration-specific success/failure instructions.

## Implementation Plan

### 1. Add shared configuration field

Add optional `persistent-instructions` support to predefined strategy configuration.


```json
{
  "persistent-instructions": "prompt_templates/persistent/default_agent_rules.md"
}
```

Prefer a template file path if the rules are reused across many strategies.

### 2. Load persistent instructions in strategy setup

Update the shared strategy loading / workflow setup path so every agent constructor can receive the rendered `persistent_instructions` string.

Requirements:

- Reuse existing prompt-template loading helpers where possible.
- Apply the same placeholder substitution model used for other prompts if dynamic values are needed.
- Keep the field optional so existing strategies continue to work unchanged.
- Do not add one-off logic in entry-point scripts if it belongs in shared strategy loading or agent construction.

### 3. Extend the base agent initialization path

Pass `persistent_instructions` through the existing agent registry construction path.

Target behavior:

```python
agent = AgentClass(
    model_name=...,
    working_dir=...,
    provider=...,
    persistent_instructions=persistent_instructions,
    ...
)
```

Existing agents should ignore the value unless they support it.

### 4. Implement Pi mapping

Update `PiRpcClient` / `PiAgent` so the RPC subprocess launch includes:

```bash
--append-system-prompt <persistent_instructions>
```

when persistent instructions are configured.

Important details:

- Preserve existing `--provider`, `--model`, `--session-dir`, `--session`, and `--fork` behavior.
- Log enough launch metadata to diagnose whether persistent instructions were configured, without dumping secrets if this field ever includes sensitive text.

### 5. Implement Codex CLI mapping

Update `CodexAgent` command construction so both initial and resumed turns include:

```bash
-c 'developer_instructions="<persistent_instructions>"'
```

Requirements:

- Add the config override to both `codex exec ... prompt` and `codex exec resume ... thread_id prompt` commands.
- Serialize the string safely as TOML. Do not manually interpolate unescaped quotes into the config argument.
- Preserve existing config overrides such as `reasoning.effort="medium"`.

### 6. Documentation updates

Update the relevant docs after implementation:

- `DEVELOPING.md`: explain `persistent-instructions` in strategy entries.
- `docs/architecture.md`: mention persistent instruction mapping in the Agents section.
- `docs/functional-spec.md`: add the optional strategy field if it becomes part of the config contract.
- `schemas/strategy_schema.json`: update if strategy JSON schema validates this new field.


## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Replacing Pi's default system prompt removes useful behavior | Use `--append-system-prompt`, not `--system-prompt` |
| Codex config string quoting breaks command execution | Serialize TOML values safely instead of hand-escaping |
| Existing strategies change behavior unexpectedly | Make the field optional and roll out gradually |
| Long sessions still drift after compaction | Keep a short critical-rules reminder in major follow-up prompts if needed |
| App-server does not accept `developer_instructions` | Treat app-server support as an investigation step and document fallback |
| Duplicating AGENTS.md content creates conflicts | Keep `AGENTS.md` repo-wide and `persistent_instructions` workflow-specific |

## Acceptance Criteria

- Pi strategies can pass persistent instructions through `--append-system-prompt`.
- Codex strategies can pass persistent instructions through `developer_instructions`.
- Existing strategies behave unchanged when the field is absent.
- Manual Pi sentinel test passes.
- Manual Codex sentinel test passes or at least prompt-input inspection confirms developer-message injection.
- Strategy schema and docs are updated if the new field is added to `predefined_strategies.json`.
- At least one Pi and one Codex workflow run completes with persistent instructions enabled.
