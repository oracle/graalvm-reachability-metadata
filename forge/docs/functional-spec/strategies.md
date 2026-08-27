# FS-workflow-strategy-registry: Predefined strategy configuration architecture

The Forge strategy component is the set of named configuration bundles in
`forge/strategies/predefined_strategies.json`. A bundle does not define a new
project goal (§FS-forge-issue-resolution-goal) or workflow contract; it selects
a registered workflow engine, agent backend, model, prompt set, parameters,
optional MCPs, and optional persistent instructions for one run, following
§FS-forge-predefined-strategy-contract and preserving the boundary in
§AR-forge-strategy-agent-boundary.

This architecture is split into the normative configuration contract
(§FS-forge-predefined-strategy-contract), the loading boundary
(§FS-predefined-strategy-loader), bundle field shape
(§FS-predefined-strategy-fields), a representative configuration example
(§FS-predefined-strategy-example), the Java fail-fix composite case
(§FS-java-fail-fix-composite-strategy-config), parameter families
(§FS-predefined-strategy-parameter-families), and the extension rule
(§FS-predefined-strategy-extension).

## FS-forge-predefined-strategy-contract: Predefined strategy configuration contract

Each predefined strategy is a named configuration bundle, not a standalone
behavior contract. The bundle selects one registered workflow engine, one agent
backend, one model, an optional reasoning level, prompt templates, workflow
parameters, optional MCPs, and optional persistent instructions. Workflow
drivers and orchestration select these bundles by name
with `--strategy-name`; the behavior they execute remains defined by the
selected workflow contract (§AR-forge-workflow-system), while the selected
backend must satisfy the agent API specified in §AR-agent-api.

Each entry in `strategies/predefined_strategies.json` must provide:

- `name` — unique identifier passed via `--strategy-name`.
- `agent` — registered agent name (`codex`, `pi`).
- `workflow` — registered workflow engine name.
- `model` — agent-visible model identifier.
- `thinking-level` — optional agent reasoning level (`off`, `minimal`, `low`,
  `medium`, `high`, `xhigh`, or `max`) for backends that support it.
- `prompts` — map of prompt-key → template path. Required keys depend on the
  workflow (e.g., `dynamic_access_iterative` requires
  `dynamic-access-iteration`).
- `persistent-instructions` — optional prompt-template path for durable,
  workflow-wide rules that should be passed to the agent backend's persistent
  instruction layer instead of a normal user prompt. The template is rendered
  with the same substitution context as workflow prompts.
- `parameters` — workflow-specific parameters (iteration limits,
  `source-context-types`, and post-generation recovery tuning such as
  `post-generation-timeout-seconds` and `post-generation-test-output-chars`,
  etc.).
- `mcps` — optional list of MCP server names.

There is no `post-generation-intervention` bundle field. The post-generation
recovery sequence (Codex metadata fix, then Pi as a last resort) is built into
the workflow base class and is not selected per strategy; see the
**Post-generation intervention** glossary entry in §FS-forge-functional-spec.

## FS-predefined-strategy-loader: Strategy loading boundary

Workflow drivers pass `--strategy-name` to the strategy loader, which resolves the
matching JSON object of the strategy contract
(§FS-forge-predefined-strategy-contract), validates it against the schema
(§root/PRCPL-verify-inputs), instantiates the selected
agent, resolves the registered workflow engine named by `workflow`, and
passes the prompt and parameter maps into that implementation. The loader owns
configuration selection; the workflow layer owns how the selected engine uses
the configuration (§AR-forge-workflow-engine, §AR-forge-workflow-boundary),
including the dynamic-access engines defined in §AR-dynamic-access-workflow that
share this loader.

```mermaid
flowchart LR
    Name["--strategy-name"]
    Json[("predefined_strategies.json")]
    Loader["strategy_loader"]
    Agent["agent backend"]
    Workflow["registered workflow engine"]
    Prompts[("prompt templates")]
    Params["parameters"]

    Name --> Loader
    Json --> Loader
    Loader --> Agent
    Loader --> Workflow
    Loader --> Prompts
    Loader --> Params
```

## FS-predefined-strategy-fields: Strategy bundle fields

Every bundle has the same architectural shape: `name` is the externally
selected key; `workflow` selects a registered workflow engine; `agent`,
`model`, and optional `thinking-level` select the editor backend, model, and
reasoning level; `prompts` maps workflow prompt
keys to template files; `parameters` supplies workflow limits and source
context choices; `mcps` enables optional MCP servers; and
`persistent-instructions` adds durable agent rules when present. Post-generation
recovery is built into the workflow base class and is not a bundle field
(§FS-forge-predefined-strategy-contract).

The currently configured source-context choices are `main`, `test`, and
`documentation`. The currently configured agent backends are `pi` and `codex`.
The currently configured workflows are `basic_iterative`,
`dynamic_access_iterative` (§AR-dynamic-access-workflow),
`optimistic_dynamic_access` (§AR-dynamic-access-bulk),
`increase_dynamic_access_coverage` (§AR-dynamic-access-composite),
`javac_iterative`, and `java_run_iterative`
(§AR-java-fail-fix-workflow).

## FS-predefined-strategy-example: Representative predefined strategy bundle

The exact active bundle list lives in `forge/strategies/predefined_strategies.json`;
this document keeps one representative example to show the architecture shape
defined in §FS-forge-predefined-strategy-contract without duplicating the
configuration file. `library_update_dynamic_access_bulk_pi_gpt-5.6-sol` selects the
`optimistic_dynamic_access` workflow (§AR-dynamic-access-bulk), the `pi`
agent, model `gpt-5.6-sol`, main-source read-only context, the
`optimistic-dynamic-access-iteration` prompt, and parameters for optimistic
iterations, test retries, source-context materialization, and the native-test
verification retry budget (§AR-native-test-verification-callers).

## FS-java-fail-fix-composite-strategy-config: Java fail-fix composite strategy configuration

Java fail-fix strategy bundles serve the two Java repair queues
(§FS-forge-scope). Bundles (§AR-java-fail-fix-workflow) that should repair
the version-bump failure and then improve dynamic-access coverage use the
`increase_dynamic_access_coverage` workflow (§AR-dynamic-access-composite) as
the configured bundle workflow. The bundle's primary workflow is
`javac_iterative` for compilation-failure issues and `java_run_iterative` for
JVM runtime-failure issues; after that primary workflow succeeds, the composite
workflow engine runs the dynamic-access coverage phase defined by
§AR-dynamic-access-workflow. The concrete bundle names, prompt template paths,
models, and iteration limits remain configuration data in
`forge/strategies/predefined_strategies.json`, not workflow-spec content.

## FS-predefined-strategy-parameter-families: Parameter families

A family is the set of bundle fields (§FS-predefined-strategy-fields) one kind of
workflow reads. The basic iterative bundles set `max-test-iterations`,
`max-failed-generations`, and `max-successful-generations`; they may also set
`max-native-test-verification-iterations` for their terminal gate
(§AR-native-test-verification-callers), which otherwise takes the shared
default. Java-fix bundles
set `max-test-iterations` and `source-context-types`. Per-class dynamic-access
bundles set `max-iterations`, `max-class-test-iterations`, and
`source-context-types`; they may also set `native-test-verification-batch-size`,
which decides how many coverage-gaining classes accumulate before the gate is
flushed (§AR-native-test-verification-callers). Optimistic dynamic-access bundles set
`max-optimistic-iterations`, `max-test-iterations`, and `source-context-types`;
Graphify variants also set `graphify-context`, and
`library_update_dynamic_access_bulk_pi_gpt-5.6-sol` also sets
`max-native-test-verification-iterations`, used by
§AR-native-test-verification-callers. Composite coverage bundles combine the
primary workflow's limits with dynamic-access limits so the selected primary
workflow can run first and the coverage phase
(§AR-dynamic-access-composite) can run afterward.

## FS-predefined-strategy-extension: Adding or changing a strategy bundle

Changing a strategy means changing a predefined configuration entry
(§FS-forge-predefined-strategy-contract) unless the desired behavior cannot
be expressed by selecting an existing registered workflow, prompt set,
parameters, agent, model, MCP list, or persistent instructions. New behavior
belongs in the workflow
architecture first (§AR-forge-drivers); the strategy configuration
should only expose it as a named bundle after the workflow contract and
implementation boundary are clear (§AR-forge-workflow-engine).
