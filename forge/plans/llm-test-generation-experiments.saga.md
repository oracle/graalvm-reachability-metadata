# Saga: LLM-Based JVM Library Test Generation Experiments

## Overview

This saga defines the execution plan for controlled experiments evaluating AI-powered test generation for JVM libraries. The experiments will populate Section 8 (Results and Insights) of the presentation defined in [`plans/presentation-massive-jvm-library-testing.md`](../plans/presentation-massive-jvm-library-testing.md).

All experiments follow the **single-variable isolation** principle:
- Change exactly one independent variable per experiment series
- Keep all other parameters fixed
- Use identical benchmark libraries across comparable runs
- Record metrics per [`schemas/run_metrics_output_schema.json`](../schemas/run_metrics_output_schema.json)

## Common Metrics

| Metric | Unit | Purpose |
|--------|------|---------|
| `input_tokens_used` | count | Cost driver |
| `output_tokens_used` | count | Cost driver |
| `iterations` | count | Efficiency indicator |
| `cost_usd` | USD | Economic viability |
| `code_coverage_percent` | % | Quality indicator |
| `metadata_entries` | count | Primary success metric |

## Benchmark Libraries

**Primary Set (Genesis):**
- `com.hazelcast:hazelcast:5.5.0` — Distributed computing, heavy reflection
- `org.hibernate:hibernate-core:5.6.14.Final` — ORM, proxies, serialization
- `io.jsonwebtoken:jjwt-jackson:0.11.5` — Crypto, smaller scope

**Extended Set (for PGO/Research experiments):**
- `com.fasterxml.jackson.core:jackson-databind:2.17.0` — Serialization-heavy
- `org.springframework:spring-context:6.1.0` — Proxy/AOP-heavy
- `io.netty:netty-all:4.1.100.Final` — JNI, native resources

## Tasks

### Task 1: Experiment Infrastructure Setup
**State:** completed

Experiment infrastructure is already in place: results directory structure, aggregation scripts, and strategy configuration framework.

---

### Task 2: Agent Comparison Experiment (Exp 2)
**State:** pending
**Prior:** Task 1

Compare different LLM coding agents/interfaces on identical tasks. This is prioritized to evaluate infrastructure requirements for supporting multiple agent backends.

**Fixed Variables:**
- Model: `oca/gpt5` (or equivalent capability tier)
- Prompts: `P1-baseline` ([`prompt_templates/initial/basic_initial.md`](../prompt_templates/initial/basic_initial.md))
- MCPs: none
- Libraries: Genesis benchmark set

**Estimated Runs:** 81 (9 variants × 3 libraries × 3 runs)

#### Subtask 2.1: Define Agent Adapter Interface
Create [`ai_workflows/agents/agent_adapter.py`](../ai_workflows/agents/agent_adapter.py) defining the abstract interface:
```python
class AgentAdapter(ABC):
    @abstractmethod
    def generate_tests(self, library: str, prompt: str, context: dict) -> AgentResult

    @abstractmethod
    def get_token_usage(self) -> TokenUsage
```

- Single-step workflow (control baseline)


#### Subtask 2.4: Implement OpenCode Adapters
Create [`ai_workflows/agents/opencode_adapter.py`](../ai_workflows/agents/opencode_adapter.py):
- `A3-opencode`: Continue.dev extension via CLI/API
- `A3-opencode-2step`: Continue.dev with planning phase

#### Subtask 2.5: Implement Cursor Adapters
Create [`ai_workflows/agents/cursor_adapter.py`](../ai_workflows/agents/cursor_adapter.py):
- `A4-cursor`: Cursor agent mode
- `A4-cursor-2step`: Cursor with planning phase

#### Subtask 2.6: Implement Kilocode Adapters
Create [`ai_workflows/agents/kilocode_adapter.py`](../ai_workflows/agents/kilocode_adapter.py):
- `A5-kilocode`: Kilocode Roo MCP-native agent
- `A5-kilocode-2step`: Kilocode with Architect mode

#### Subtask 2.7: Implement 2-Step Workflow Orchestrator
Create [`ai_workflows/agents/two_step_orchestrator.py`](../ai_workflows/agents/two_step_orchestrator.py) to manage:
1. **Architect Phase:** Analyze library, produce feature inventory, test plan, structure
2. **Code Phase:** Receive architect output as context, generate tests

#### Subtask 2.8: Create Agent Experiment Strategies
Add entries to [`strategies/experiment_strategies.json`](../strategies/experiment_strategies.json) for each agent variant:
```json
{
  "experiment_id": "exp2",
  "two_step": false,
  ...
}
```

#### Subtask 2.9: Execute Agent Comparison Runs
Run each agent variant 3× per Genesis library:
```bash
python benchmarks/benchmark_runner.py \
  --library "com.hazelcast:hazelcast:5.5.0" --runs 3
```

#### Subtask 2.10: Analyze Agent Results
Success criteria:
- Identify agent with lowest iteration count for equivalent coverage
- Measure cost variance across agents
- Compare 1-step vs 2-step workflow effectiveness

---

### Task 3: PGO and Source Analysis Experiment (Exp 6)
**State:** pending
**Prior:** Task 1

Leverage Profile-Guided Optimization (PGO) data and static analysis to inform test generation. This is prioritized for its research exploration value.

**Fixed Variables:**
- Model: `oca/gpt5`
- Libraries: Genesis + Extended benchmark set (with PGO profiles)

**Estimated Runs:** 45 (5 variants × 3 libraries × 3 runs, expandable to extended set)

#### Subtask 3.1: Define PGO Data Schema
Create [`schemas/pgo_profile_schema.json`](../schemas/pgo_profile_schema.json):
```json
{
  "hot_methods": [
    {"class": "com.example.Foo", "method": "bar", "invocations": 10000}
  ],
  "reflection_targets": [
    {"class": "com.example.Config", "accessed_fields": ["host", "port"]}
  ]
}
```

#### Subtask 3.2: Implement PGO Profile Generator
Create [`ai_workflows/context_enrichment/pgo_generator.py`](../ai_workflows/context_enrichment/pgo_generator.py):
- Extract hot methods from existing test runs or production traces
- Parse JFR/async-profiler output
- Generate PGO JSON conforming to schema

#### Subtask 3.3: Implement Source Context Extractor
Create [`ai_workflows/context_enrichment/source_extractor.py`](../ai_workflows/context_enrichment/source_extractor.py):
- Decompile JAR files using CFR/Procyon
- Extract relevant source snippets for context window
- Implement size-aware truncation for large libraries

#### Subtask 3.4: Implement Call Graph Analyzer
Create [`ai_workflows/context_enrichment/callgraph_analyzer.py`](../ai_workflows/context_enrichment/callgraph_analyzer.py):
- Static analysis using ASM or Soot
- Generate method call relationships
- Format for LLM consumption

#### Subtask 3.5: Create Context Enrichment Injector
Create [`ai_workflows/context_enrichment/enrichment_injector.py`](../ai_workflows/context_enrichment/enrichment_injector.py):
- Combine PGO, source, and callgraph data
- Inject into prompt context
- Track additional token overhead

#### Subtask 3.6: Create Enrichment-Aware Prompts
Create prompt templates in [`prompt_templates/enrichment/`](../prompt_templates/enrichment/):
- `pgo_enriched.md` — Template with PGO hot method guidance
- `source_enriched.md` — Template with source context
- `combined_enriched.md` — Template with full enrichment

#### Subtask 3.7: Create PGO Experiment Strategies
Add entries to [`strategies/experiment_strategies.json`](../strategies/experiment_strategies.json):

| Config ID | Enrichment | Description |
|-----------|------------|-------------|
| `E1-none` | No enrichment | Control |
| `E2-pgo` | PGO profile injection | Provide hot method list to LLM |
| `E3-source` | Library source in context | Add decompiled/source JAR contents |
| `E4-callgraph` | Static call graph | Provide method call relationships |
| `E5-combined` | PGO + source + callgraph | Full enrichment |

#### Subtask 3.8: Generate PGO Profiles for Benchmark Libraries
Run existing tests on Genesis libraries to generate PGO profiles:
- Instrument with JFR
- Extract hot methods and reflection targets
- Store in `benchmarks/pgo_profiles/`

#### Subtask 3.9: Execute PGO Experiment Runs
Run each enrichment configuration 3× per library:
```bash
python benchmarks/benchmark_runner.py \
  --experiment exp6 --variant E2-pgo \
  --library "com.hazelcast:hazelcast:5.5.0" --runs 3
```

#### Subtask 3.10: Analyze PGO Results
Additional metrics to track:
- `pgo_methods_covered`: Hot methods exercised by generated tests
- `reflection_coverage`: PGO-identified reflection targets covered

Success criteria:
- Measure correlation between PGO guidance and metadata generation
- Quantify diminishing returns of context size
- Determine optimal enrichment configuration

---

### Task 4: Model Comparison Experiment (Exp 3)
**State:** pending
**Prior:** Task 1

Evaluate how model choice affects generation quality and cost. Core economic question for production deployment.

**Fixed Variables:**
- Prompts: `P1-baseline`
- MCPs: none
- Libraries: Genesis benchmark set

**Estimated Runs:** 63 (7 models × 3 libraries × 3 runs)

#### Subtask 4.1: Configure Model Variants
Create strategy entries in [`strategies/experiment_strategies.json`](../strategies/experiment_strategies.json):

| Model ID | Model | Approx. Cost Tier |
|----------|-------|-------------------|
| `M1-gpt5` | `oca/gpt5` | High |
| `M2-gpt54` | `oca/gpt-5.4` | Medium-High |
| `M3-claude-opus` | `anthropic/claude-opus-4` | High |
| `M4-claude-sonnet` | `anthropic/claude-sonnet-4` | Medium |
| `M5-gemini-pro` | `google/gemini-2.5-pro` | Medium |
| `M6-deepseek` | `deepseek/deepseek-v3` | Low |
| `M7-llama` | `meta/llama-4-maverick` | Low |

#### Subtask 4.2: Implement Model-Specific Token Pricing
Extend [`utility_scripts/metrics_writer.py`](../utility_scripts/metrics_writer.py) to:
- Track per-model token pricing
- Calculate cost_usd based on model-specific rates
- Support pricing configuration file

#### Subtask 4.3: Execute Model Comparison Runs
Run each model 3× per Genesis library with identical prompts.

#### Subtask 4.4: Analyze Model Results
Success criteria:
- Plot `metadata_entries` vs `cost_usd` per model (Pareto frontier)
- Identify cost-optimal model for production use

---

### Task 5: Prompt Variations Experiment (Exp 1)
**State:** pending
**Prior:** Task 1

Measure how prompt wording affects test quality and generation efficiency. Low-cost, high-insight experiment.

**Fixed Variables:**
- Model: `oca/gpt5`
- MCPs: none
- Libraries: Genesis benchmark set
- Strategy parameters: `max-test-iterations=5`, `max-failed-generations=2`, `max-successful-generations=3`

**Estimated Runs:** 45 (5 variants × 3 libraries × 3 runs)

#### Subtask 5.1: Create Prompt Variant Files
Create variants in [`prompt_templates/experiments/exp1-prompts/`](../prompt_templates/experiments/exp1-prompts/):

| Variant ID | Change | Hypothesis |
|------------|--------|------------|
| `P1-baseline` | Current [`basic_initial.md`](../prompt_templates/initial/basic_initial.md) | Control |
| `P2-verbose` | Add explicit coverage targets | Higher coverage, more iterations |
| `P3-examples` | Include 2-3 sample test snippets | Faster convergence, fewer failures |
| `P4-minimal` | Remove role preamble, keep only task/rules | Lower token cost, comparable quality |
| `P5-cot` | Add chain-of-thought instruction | Better feature discovery |

#### Subtask 5.2: Create Prompt Experiment Strategies
Add entries to [`strategies/experiment_strategies.json`](../strategies/experiment_strategies.json) for each prompt variant.

#### Subtask 5.3: Execute Prompt Experiment Runs
Run each variant 3× per Genesis library.

#### Subtask 5.4: Analyze Prompt Results
Success criteria:
- Identify prompt variant with best `metadata_entries / cost_usd` ratio
- Document coverage variance across prompts

---

### Task 6: MCP Tools Experiment (Exp 4)
**State:** pending
**Prior:** Task 1

Measure impact of additional context tools on test quality. Validates MCP investment.

**Fixed Variables:**
- Model: `oca/gpt5`
- Prompts: `P1-baseline`
- Libraries: Genesis benchmark set

**Estimated Runs:** 54 (6 variants × 3 libraries × 3 runs)

#### Subtask 6.1: Verify MCP Server Availability
Ensure MCP servers are configured and accessible:
- `javadoc_search`
- `context7`
- `web_search`
- `github_repo_search`

#### Subtask 6.2: Create MCP Configuration Variants

| Config ID | MCPs Enabled | Purpose |
|-----------|--------------|---------|
| `T1-none` | `[]` | Control |
| `T2-javadoc` | `["javadoc_search"]` | API documentation access |
| `T3-context7` | `["context7"]` | Library usage examples |
| `T4-both` | `["javadoc_search", "context7"]` | Combined context |
| `T5-web` | `["web_search"]` | General web context |
| `T6-repo` | `["github_repo_search"]` | Source code access |

#### Subtask 6.3: Extend Metrics Schema for MCP Tracking
Update [`schemas/run_metrics_output_schema.json`](../schemas/run_metrics_output_schema.json) to include:
- `mcp_calls`: Number of MCP tool invocations
- `mcp_tokens`: Tokens consumed by MCP responses

#### Subtask 6.4: Execute MCP Experiment Runs
Run each configuration 3× per Genesis library.

#### Subtask 6.5: Analyze MCP Results
Success criteria:
- Quantify coverage lift from MCP usage
- Determine if MCP cost is justified by quality improvement

---

### Task 7: Research Integration Experiment (Exp 5)
**State:** pending
**Prior:** Task 1, Task 3

Evaluate automated research-driven test generation. Advanced methodology building on PGO insights.

**Fixed Variables:**
- Model: `oca/gpt5`
- Libraries: Genesis benchmark set

**Estimated Runs:** 36 (4 variants × 3 libraries × 3 runs)

#### Subtask 7.1: Implement Research Phase
Create [`ai_workflows/research/research_phase.py`](../ai_workflows/research/research_phase.py):
1. Identify library's primary use cases from documentation
2. List dynamic features (reflection, serialization, proxies)
3. Prioritize features by Native Image relevance
4. Generate feature-test mapping

#### Subtask 7.2: Create Research Prompts
Create templates in [`prompt_templates/research/`](../prompt_templates/research/):
- `research_initial.md` — Pre-generation research prompt
- `research_on_failure.md` — Triggered research prompt
- `research_interleaved.md` — Continuous research prompt

#### Subtask 7.3: Create Research Mode Variants

| Mode ID | Configuration | Description |
|---------|---------------|-------------|
| `R1-none` | Standard workflow | Control |
| `R2-prereq` | Pre-generation research | LLM researches before coding |
| `R3-iterative` | Research on failure | Trigger research when tests fail |
| `R4-continuous` | Interleaved research | Research between each generation |

#### Subtask 7.4: Execute Research Experiment Runs
Run each mode 3× per Genesis library.

#### Subtask 7.5: Analyze Research Results
Success criteria:
- Measure reduction in failed generations
- Evaluate feature discovery completeness

---

### Task 8: Results Aggregation and Visualization
**State:** pending
**Prior:** Task 2, Task 3, Task 4, Task 5, Task 6, Task 7

Aggregate all experiment results and produce deliverables for Section 8 of the presentation.

#### Subtask 8.1: Run Aggregation Script
Execute [`benchmarks/aggregate_results.py`](../benchmarks/aggregate_results.py) across all experiment directories.

#### Subtask 8.2: Generate Statistical Summaries
Produce CSV exports with:
- Mean, stddev, min, max per configuration
- Cross-experiment comparisons
- Cost-effectiveness rankings

#### Subtask 8.3: Create Visualizations
Generate charts for presentation:
- **Box plots:** Metrics by experiment variable
- **Scatter plots:** Coverage vs cost
- **Pareto charts:** Multi-objective optimization
- **Heatmaps:** Agent × Model performance matrix

#### Subtask 8.4: Compile Key Findings
Document insights for each experiment:
- Best performing configuration
- Cost-quality tradeoffs
- Recommendations for production deployment

#### Subtask 8.5: Update Presentation Section 8
Integrate results into [`plans/presentation-massive-jvm-library-testing.md`](../plans/presentation-massive-jvm-library-testing.md) Section 8.
