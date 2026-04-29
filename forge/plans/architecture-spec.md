# metadata-forge Architecture Spec

## Overview

metadata-forge automates GraalVM reachability metadata management using AI-powered workflows. The system processes GitHub issues, generates tests, fixes compilation failures, and creates PRs — all orchestrated through configurable strategies.

---

## Architecture Pattern: Bridge

The architecture follows the **Bridge pattern** — decoupling the abstraction (Workflow) from its implementation (Agent) so both can vary independently. A Strategy composes one Workflow with one Agent at runtime, both resolved dynamically by name from configuration.

Both Agent and WorkflowStrategy use a **self-registering registry pattern** — subclasses register themselves under a key, and are resolved at runtime from the strategy config. This makes both hierarchies open for extension without modifying existing code.

```mermaid
classDiagram
    class Agent {
        <<abstract>>
        +send_prompt(prompt: str) str
        +fork(prompt: str) Agent
        +compact_fork(prompt: str) Agent
        +clear_context() void
        +run_test_command(test_cmd: str) str
        +total_tokens_sent: int
        +total_tokens_received: int
        +register(agent_key: str)$ decorator
        +get_class(agent_name: str)$ Agent
        -_registry: dict~str, type~
    }

        -_coder: Coder
        +__init__(model_name, editable_files, read_only_files, verbose)
        +send_prompt(prompt) str
        +fork(prompt) Agent
        +compact_fork(prompt) Agent
        +clear_context() void
        +run_test_command(cmd) str
    }

    class CodexAgent {
        -_model_name: str
        -_working_dir: str
        -_timeout: int
        +__init__(model_name, working_dir, timeout)
        +send_prompt(prompt) str
        +fork(prompt) Agent
        +compact_fork(prompt) Agent
        +clear_context() void
        +run_test_command(cmd) str
    }

    class WorkflowStrategy {
        <<abstract>>
        +strategy_obj: dict
        +context: dict
        +prompts: dict
        +parameters: dict
        +run(agent: Agent, **kwargs)*
        +_run_test_with_retry(library: str) bool
        +_run_command(cmd: str) str
        +_get_first_failed_task(output: str) str
        +_load_prompt(key: str) str
        +register(strategy_key: str)$ decorator
        +get_class(workflow_name: str)$ WorkflowStrategy
        -_registry: dict~str, type~
    }

    class BasicIterativeStrategy {
        +run(agent: Agent, checkpoint_commit_hash: str)
    }

    class JavacIterativeStrategy {
        +run(agent: Agent)
    }

    class NativeImageDynamicAccessStrategy {
        +run(agent: Agent)
    }

    class StrategyConfig {
        <<JSON>>
        +name: str
        +agent: str
        +workflow: str
        +model: str
        +prompts: dict
        +parameters: dict
        +mcps: list
    }

    Agent <|-- CodexAgent : @Agent.register("codex")
    WorkflowStrategy <|-- BasicIterativeStrategy : @WorkflowStrategy.register("basic_iterative")
    WorkflowStrategy <|-- JavacIterativeStrategy : @WorkflowStrategy.register("javac_iterative")
    WorkflowStrategy <|-- NativeImageDynamicAccessStrategy : @WorkflowStrategy.register("native_image_dynamic_access")

    WorkflowStrategy o-- Agent : uses via interface

    StrategyConfig ..> Agent : agent field → Agent.get_class()
    StrategyConfig ..> WorkflowStrategy : workflow field → WorkflowStrategy.get_class()
```

### Dynamic Resolution

Both hierarchies use the same registry pattern:

- **`WorkflowStrategy.get_class(workflow_name)`** — resolves workflow class from `strategy["workflow"]`
- **`Agent.get_class(agent_name)`** — resolves agent class from `strategy["agent"]`

Entry-point scripts call both to construct the workflow and agent, then compose them via `workflow.run(agent, ...)`.

---

## Core Concepts

### Agent
An **Agent** is an AI coding tool that can receive instructions, generate/modify code, and provide feedback. Agents differ in their interaction model (stateful vs stateless) but expose a uniform interface to workflows.

Agents are **dynamically registered and resolved** using `@Agent.register("key")` and `Agent.get_class("key")`, mirroring how workflows are registered.

**Known agents:**
- **Codex** — stateless CLI agent. Each invocation is independent with no memory of prior calls. Operates on the entire working directory.

**Agent interface:**
| Method | Purpose |
|--------|---------|
| `send_prompt(prompt) -> str` | Send an instruction to the agent, get response |
| `fork(prompt) -> Agent` | Create a child branch from the current agent state and send the prompt to that branch |
| `compact_fork(prompt) -> Agent` | Create a summarized child branch from the current agent state and send the prompt to that branch |
| `clear_context()` | Reset conversation history. No-op for stateless agents |
| `run_test_command(test_cmd) -> str` | Execute a test and internalize results for context |
| `total_tokens_sent: int` | Cumulative input token count |
| `total_tokens_received: int` | Cumulative output token count |

**Agent constructors:**

```python
    model_name: str,           # e.g. "oca/gpt-5.4"
    editable_files: list[str], # files the agent can modify
    read_only_files: list[str],# context-only files
    verbose: bool = False,
)
```

CodexAgent wraps the Codex CLI:
```python
CodexAgent(
    model_name: str,       # e.g. "oca/gpt-5.4"
    working_dir: str,      # directory to operate in
    timeout: int = 600,    # subprocess timeout in seconds
)
```

### Workflow
A **Workflow** defines the iteration pattern: how to loop, when to retry, when to checkpoint, when to give up. Workflows are agent-agnostic — they interact with agents only through the Agent interface.

Workflows are **dynamically registered and resolved** using `@WorkflowStrategy.register("key")` and `WorkflowStrategy.get_class("key")`. Workflow creation and the registry pattern stay the same as today — only the `run()` signature changes to accept `Agent` instead of `coder`.

**Known workflows:**
- **basic_iterative** — Generate tests iteratively with checkpoint/rollback. Tracks successful and failed generations, resets to checkpoint on failure. Used for adding new library support.
- **javac_iterative** — Fix compilation errors iteratively until tests pass or max iterations reached. Used for library version bumps.
- **native_image_dynamic_access** — Collect dynamic-access evidence from GraalVM Native Image tooling, attribute the missing metadata to concrete library call sites, and iteratively update metadata until the native-image workflow passes. Used for `fails-native-image-run` issues.

### Strategy
A **Strategy** is the combination of an **Agent** and a **Workflow**, plus their configuration (model, prompts, parameters). Strategies are defined in `strategies/predefined_strategies.json`. The `workflow` and `agent` fields independently reference registered implementations.

### Metadata Fix Utility (`fix_metadata_codex.py`)
Metadata fixing via Codex is a **standalone utility**, not an agent concern. It is always performed by Codex regardless of which agent is used for test generation. It runs as a fallback when tests fail after metadata generation. This lives in `fix_metadata_codex.py` (renamed from `codex_runner.py`) and is called by workflow infrastructure (`_run_test_with_retry`).

### Dynamic Access Discovery
Native Image dynamic-access discovery is a **workflow utility concern**, not an agent concern. The goal is to identify which library methods trigger dynamic features at run time and therefore require reachability metadata.

This workflow relies on two complementary GraalVM data sources:

1. **Tracing Agent on the JVM** — run the library tests on the GraalVM JDK with the Native Image tracing agent enabled. This captures dynamic feature usage such as reflection, resources, JNI, proxies, serialization, and predefined classes into JSON output.
2. **Native Image Build Report Dynamic Access view** — when available, build with dynamic-access reporting enabled to obtain call-site level information that shows where dynamic access originates and whether metadata is already being provided by another classpath entry.

The tracing output is the machine-readable source of truth for generated metadata. The build report is the attribution layer used to answer the question "which methods in the target library are responsible for the missing metadata?"

The workflow should normalize both data sources into a single internal findings model:

```json
{
  "library": "group:artifact:version",
  "metadata_type": "reflection|resource|jni|proxy|serialization|predefined-class",
  "target_class": "fqcn",
  "target_member": "method/field/signature or null",
  "call_location": "fqcn#method or source location",
  "evidence_source": "trace-output|build-report",
  "metadata_owner": "classpath entry that should own the metadata",
  "status": "missing|already-covered|ambiguous"
}
```

The agent does **not** discover dynamic access by guessing from stack traces alone. Instead, the workflow passes structured findings derived from GraalVM tooling and asks the agent to:
- map findings to the correct metadata directory in the reachability-metadata repo
- write the minimal `reachability-metadata.json` entries needed for the target library
- preserve existing metadata ownership boundaries when another classpath entry already provides the metadata

This keeps the discovery mechanism deterministic while still letting the agent perform the code-editing and reasoning step.

---

## Strategy Configuration Schema

```json
{
    "name": "string — unique identifier",
    "description": "string — human-readable description",
    "workflow": "string — workflow type: 'basic_iterative' | 'javac_iterative' | 'native_image_dynamic_access' (resolved via WorkflowStrategy.get_class())",
    "model": "string — model identifier (e.g., 'oca/gpt-5.4')",
    "prompts": {
        "prompt_key": "path/to/template.md"
    },
    "parameters": {
        "max-test-iterations": "int",
        "max-failed-generations": "int (basic_iterative only)",
        "max-successful-generations": "int (basic_iterative only)",
        "dynamic-access-mode": "string: 'trace' | 'build-report' | 'hybrid' (native_image_dynamic_access only)",
        "trace-output-dir": "string path (native_image_dynamic_access only)",
        "build-report-dir": "string path (native_image_dynamic_access only)",
        "max-dynamic-access-iterations": "int (native_image_dynamic_access only)"
    },
    "mcps": ["list of MCP server names (optional)"]
}
```

Both `workflow` and `agent` fields are resolved dynamically via their respective registries. This allows any agent to be paired with any workflow.

---

## Module Structure

```
metadata-forge/
├── ai_workflows/
│   ├── agents/                          # Agent abstraction layer
│   │   ├── agent.py                     # Agent ABC + registry
│   │   └── codex_agent.py              # Wraps Codex CLI
│   ├── workflow_strategies/             # Workflow implementations (unchanged pattern)
│   │   ├── workflow_strategy.py         # WorkflowStrategy ABC + registry
│   │   ├── basic_iterative_strategy.py  # Add-new-library workflow
│   │   ├── javac_iterative_strategy.py  # Fix-javac workflow
│   │   └── native_image_dynamic_access_strategy.py # Native-image dynamic-access workflow
│   ├── fix_metadata_codex.py            # Metadata fix utility (always Codex, renamed from codex_runner.py)
│   ├── add_new_library_support.py       # Entry point: new library workflow
│   ├── fix_javac_fail.py                # Entry point: javac fix workflow
│   └── fix_native_image_dynamic_access.py # Entry point: native-image dynamic-access workflow
├── complete_pipelines/                  # End-to-end: AI workflow + PR creation
├── git_scripts/                         # GitHub/Git automation
├── utility_scripts/                     # Helpers (metrics, strategy loading, tracing, parsing, etc.)
├── strategies/
│   └── predefined_strategies.json       # Strategy definitions
├── prompt_templates/                    # Agent prompt templates
└── forge_metadata.py                    # Top-level issue orchestrator
```

---

## Data Flow

```
GitHub Issue
    │
    ▼
forge_metadata.py (claim issue, select pipeline by label)
    │
    ▼
Entry-point script (add_new_library_support.py / fix_javac_fail.py)
    ├── Load strategy from predefined_strategies.json
    ├── AgentClass = Agent.get_class(strategy["agent"])
    ├── WorkflowClass = WorkflowStrategy.get_class(strategy["workflow"])
    ├── agent = AgentClass(model_name=..., ...)
    ├── workflow = WorkflowClass(strategy_obj=strategy, **context)
    └── workflow.run(agent, ...)
          │
          ▼
    WorkflowStrategy.run(agent: Agent, ...)
        ├── agent.send_prompt(prompt)     # Generate/fix code
        ├── agent.clear_context()         # Reset between generations
        ├── agent.run_test_command(cmd)    # Run tests with context feedback
        ├── _run_test_with_retry(lib)      # Metadata fix fallback (always Codex via fix_metadata_codex.py)
        └── Return (iterations, results)
          │
          ▼
    Metrics collection (agent.total_tokens_sent/received)
    PR creation (git_scripts/)
```

### Data Flow: Native Image Dynamic Access Workflow

```
GitHub Issue labeled fails-native-image-run
    │
    ▼
forge_metadata.py
    │
    ▼
fix_native_image_dynamic_access.py
    ├── load strategy
    ├── resolve agent + workflow
    ├── prepare target version/branch
    └── workflow.run(agent, ...)
          │
          ▼
    NativeImageDynamicAccessStrategy.run(agent)
        ├── run JVM tests with Native Image tracing agent enabled
        ├── optionally run native-image build/report with dynamic-access reporting
        ├── parse trace/build-report into structured findings
        ├── filter findings to the target library
        ├── prompt agent with concrete call locations + missing metadata types
        ├── update metadata/<group>/<artifact>/<version>/reachability-metadata.json
        ├── rerun native-image verification task
        └── stop when dynamic-access findings are covered or retries exhausted
```

---

## Repo Implementation Plan For Dynamic Access

### New workflow strategy
Add `ai_workflows/workflow_strategies/native_image_dynamic_access_strategy.py` registered as `native_image_dynamic_access`.

Responsibilities:
- orchestrate trace collection, parsing, prompting, retrying, and verification
- keep agent usage limited to editing and reasoning over already-collected findings
- avoid placing GraalVM command-line details inside agent prompts

### New entry point
Add `ai_workflows/fix_native_image_dynamic_access.py`.

Responsibilities:
- parse CLI flags for current coordinates, new version, strategy name, and optional output directories
- prepare the target version in the reachability-metadata repo
- instantiate the configured `Agent`
- instantiate `NativeImageDynamicAccessStrategy`
- write run metrics and return a process exit code suitable for pipeline use

### New utility scripts
Add the following utility modules under `utility_scripts/`:

- `native_image_trace_runner.py`
  - runs JVM tests with the GraalVM tracing agent enabled
  - supports `trace-output`, `config-output-dir`, and config merge directories
  - resolves paths with `os.path`
- `dynamic_access_report_runner.py`
  - runs the native-image verification step with build-report dynamic-access flags
  - stores report artifacts under a deterministic work directory
- `dynamic_access_parser.py`
  - parses tracing-agent output JSON and optional build-report artifacts
  - emits normalized findings objects for the workflow
- `dynamic_access_filter.py`
  - filters findings to those owned by the target library version
  - marks cases already covered by existing metadata or by another provider

### Prompt contract for the agent
The workflow should not hand the agent raw logs only. Instead, it should build a prompt that contains:
- the target coordinates
- the current metadata file path
- a compact findings table derived from tracing/build-report data
- the verification command to rerun
- explicit ownership constraints: only update metadata for the target library

### Integration with the current repo
The cleanest integration path is to make this workflow the structured replacement for the current `fails-native-image-run` path:

1. Keep `complete_pipelines/fix_ni_run_create_pr.py` as the top-level issue pipeline wrapper.
2. Replace its direct "run Codex if Gradle fails" fallback with a call into `ai_workflows/fix_native_image_dynamic_access.py`.
3. Reuse the existing PR creation path once verification passes.
4. Keep `fix_metadata_codex.py` as a low-level emergency fallback, but prefer the dynamic-access workflow because it has stronger evidence about which metadata is missing and where it originates.

### Why this belongs in metadata-forge
This repository already owns:
- strategy selection
- AI workflow orchestration
- metrics collection
- issue/pipeline routing

Dynamic-access discovery fits naturally here because it is orchestration-heavy and combines GraalVM tool execution, artifact parsing, and AI-assisted metadata editing. The actual metadata changes still happen in the reachability-metadata repository; metadata-forge remains the control plane.

---

## Behavioral Contracts

### Agent.clear_context()
- **CodexAgent**: Drops the stored session id and adapter-maintained branch history so the next call starts fresh

### Agent.fork(prompt)
- **CodexAgent**: Creates a child branch that reconstructs full prior context into the next prompt, then sends the prompt on that child

### Agent.compact_fork(prompt)
- **CodexAgent**: Creates a child branch from a compact textual summary of the current branch, then sends the prompt on that child

### Agent.run_test_command(cmd)
- **CodexAgent**: Runs the command via subprocess. Since Codex is stateless, test output must be explicitly included in the next `send_prompt()` call if needed by the workflow

### Metadata Fix Fallback (_run_test_with_retry)
- Always uses Codex via `fix_metadata_codex.py`, independent of the primary agent
- Runs after test failure post-metadata-generation
- Workflow infrastructure concern, not an agent method

### Dynamic Access Collection
- Dynamic-access collection is performed by workflow utilities, not by `Agent.run_test_command()`
- The tracing agent and build-report runner produce structured artifacts consumed by the workflow
- The agent receives normalized findings, not responsibility for invoking GraalVM tooling itself
- The workflow is responsible for deciding whether a finding is owned by the target library or already covered elsewhere

---

## Invariants

1. **Workflows never import or reference specific agent implementations** — they depend only on the Agent interface
3. **Both Agent and WorkflowStrategy use dynamic registry resolution** — `Agent.get_class()` and `WorkflowStrategy.get_class()` mirror each other
4. **Metadata fix is always Codex** — it's a specialized utility (`fix_metadata_codex.py`), not a general agent capability
5. **Strategy = Agent + Workflow + Config** — the strategy JSON fully describes how to run a pipeline
6. **Workflow creation pattern is unchanged** — same `@register` decorator, same `get_class()` lookup, only `run()` signature changes to accept `Agent`
7. **Prompt templates are agent-aware** — different agents may need different prompt styles; this is handled by pointing to different template files in the strategy config, not by agent-level logic
8. **Dynamic-access evidence comes from GraalVM tooling** — workflows may parse tracing-agent and build-report artifacts, but agents should not be the primary discovery mechanism
9. **Metadata ownership is explicit** — the workflow must not add metadata for third-party classpath entries when the evidence shows that another entry already provides or should provide it
