# Experiment Specification: LLM-Based JVM Library Test Generation

## Purpose

Define controlled experiments to evaluate the effectiveness of different variables in AI-powered test generation for JVM libraries. Results will populate Section 8 (Results and Insights) of the presentation.

---

## Experiment Design Principles

All experiments follow the **single-variable isolation** principle:
- Change exactly one independent variable per experiment series
- Keep all other parameters fixed
- Use identical benchmark libraries across comparable runs
- Record metrics per [`schemas/run_metrics_output_schema.json`](../schemas/run_metrics_output_schema.json)

**Common Metrics Captured:**
| Metric | Unit | Purpose |
|--------|------|---------|
| `input_tokens_used` | count | Cost driver |
| `output_tokens_used` | count | Cost driver |
| `iterations` | count | Efficiency indicator |
| `cost_usd` | USD | Economic viability |
| `code_coverage_percent` | % | Quality indicator |
| `metadata_entries` | count | Primary success metric |

---

## Experiment 1: Prompt Variations

**Objective:** Measure how prompt wording affects test quality and generation efficiency.

### Fixed Variables
- Model: `oca/gpt5`
- MCPs: none
- Libraries: Genesis benchmark set
- Strategy parameters: `max-test-iterations=5`, `max-failed-generations=2`, `max-successful-generations=3`

### Independent Variable: Prompt Templates

Create alternative prompt sets in `prompt_templates/experiments/`:

| Variant ID | Initial Prompt Change | Hypothesis |
|------------|----------------------|------------|
| `P1-baseline` | Current [`basic_initial.md`](../prompt_templates/initial/basic_initial.md) | Control |
| `P2-verbose` | Add explicit coverage targets (e.g., "aim for 40% line coverage") | Higher coverage, more iterations |
| `P3-examples` | Include 2-3 sample test snippets | Faster convergence, fewer failures |
| `P4-minimal` | Remove role preamble, keep only task/rules | Lower token cost, comparable quality |
| `P5-cot` | Add chain-of-thought instruction ("First list features, then test each") | Better feature discovery |

### Procedure
1. Create prompt variant files under `prompt_templates/experiments/exp1-prompts/`
2. Create corresponding strategy entries in `strategies/experiment_strategies.json`
3. Run each variant 3× per library (statistical significance)
4. Record all metrics

### Success Criteria
- Identify prompt variant with best `metadata_entries / cost_usd` ratio
- Document coverage variance across prompts

---

## Experiment 2: Coding Agent Comparison

**Objective:** Compare different LLM coding agents/interfaces on identical tasks.

### Fixed Variables
- Model: `oca/gpt5` (or equivalent capability tier)
- Prompts: `P1-baseline`
- MCPs: none
- Libraries: Genesis benchmark set

### Independent Variable: Coding Agent

| Agent ID | Implementation | Notes |
|----------|---------------|-------|
| `A3-opencode` | Continue.dev extension | IDE-integrated agent |
| `A3-opencode-2step` | Continue.dev with planning | Architect → Code workflow |
| `A4-pi` | Cursor agent mode | Alternative agent architecture |
| `A4-pi-2step` | Cursor with planning phase | Architect → Code workflow |
| `A5-kilocode` | Kilocode Roo | MCP-native agent |
| `A5-kilocode-2step` | Kilocode with Architect mode | Architect → Code workflow |

### 2-Step (Planning-First) Workflow

The `-2step` variants implement a two-phase approach:

1. **Architect Phase:** LLM analyzes the library and produces:
   - Feature inventory (reflection, serialization, proxies, JNI, etc.)
   - Test plan with prioritized coverage targets
   - Suggested test structure/organization

2. **Code Phase:** LLM receives the architect output as context and generates tests accordingly

**Rationale:** Planning-first may reduce wasted iterations by establishing a coherent strategy before coding, at the cost of additional tokens for the planning phase.

### Procedure
1. Implement adapter scripts in `ai_workflows/agents/` for each agent
2. Ensure prompt delivery is equivalent across agents
3. Run each agent 3× per library
4. Normalize metrics (agents may report tokens differently)

### Success Criteria
- Identify agent with lowest iteration count for equivalent coverage
- Measure cost variance across agents

---

## Experiment 3: Model Comparison

**Objective:** Evaluate how model choice affects generation quality and cost.

### Fixed Variables
- Prompts: `P1-baseline`
- MCPs: none
- Libraries: Genesis benchmark set
- Strategy parameters: identical

### Independent Variable: LLM Model

| Model ID | Model | Approx. Cost Tier |
|----------|-------|-------------------|
| `M1-gpt5` | `oca/gpt5` | High |
| `M2-gpt54` | `oca/gpt-5.4` | Medium-High |
| `M3-claude-opus` | `anthropic/claude-opus-4` | High |
| `M4-claude-sonnet` | `anthropic/claude-sonnet-4` | Medium |
| `M5-gemini-pro` | `google/gemini-2.5-pro` | Medium |
| `M6-deepseek` | `deepseek/deepseek-v3` | Low |
| `M7-llama` | `meta/llama-4-maverick` | Low |

### Procedure
1. Create strategy variants in [`strategies/predefined_strategies.json`](../strategies/predefined_strategies.json) per model
2. Run each model 3× per library
3. Track token pricing separately (models have different rates)

### Success Criteria
- Plot `metadata_entries` vs `cost_usd` per model (Pareto frontier)
- Identify cost-optimal model for production use

---

## Experiment 4: Tools and MCP Extensions

**Objective:** Measure impact of additional context tools on test quality.

### Fixed Variables
- Model: `oca/gpt5`
- Prompts: `P1-baseline`
- Libraries: Genesis benchmark set

### Independent Variable: MCP Configuration

| Config ID | MCPs Enabled | Purpose |
|-----------|--------------|---------|
| `T1-none` | `[]` | Control |
| `T2-javadoc` | `["javadoc_search"]` | API documentation access |
| `T3-context7` | `["context7"]` | Library usage examples |
| `T4-both` | `["javadoc_search", "context7"]` | Combined context |
| `T5-web` | `["web_search"]` | General web context |
| `T6-repo` | `["github_repo_search"]` | Source code access |

### Procedure
1. Ensure MCP servers are available and configured
2. Create strategy variants per MCP configuration
3. Run each configuration 3× per library
4. Track MCP call counts alongside standard metrics

### Additional Metrics
- `mcp_calls`: Number of MCP tool invocations
- `mcp_tokens`: Tokens consumed by MCP responses

### Success Criteria
- Quantify coverage lift from MCP usage
- Determine if MCP cost is justified by quality improvement

---

## Experiment 5: PI-Auto-Research Integration

**Objective:** Evaluate automated research-driven test generation using PI-Auto-Research methodology.

### Fixed Variables
- Model: `oca/gpt5`
- Libraries: Genesis benchmark set

### Independent Variable: Research Mode

| Mode ID | Configuration | Description |
|---------|---------------|-------------|
| `R1-none` | Standard workflow | Control |
| `R2-prereq` | Pre-generation research phase | LLM researches library before coding |
| `R3-iterative` | Research on failure | Trigger research when tests fail |
| `R4-continuous` | Interleaved research | Research between each generation |

### Research Phase Specification
The research phase prompts the LLM to:
1. Identify library's primary use cases from documentation
2. List dynamic features (reflection, serialization, proxies)
3. Prioritize features by Native Image relevance
4. Generate a feature-test mapping before coding

### Procedure
1. Implement research phase in `ai_workflows/research/`
2. Create prompts in `prompt_templates/research/`
3. Integrate with strategy execution flow
4. Run each mode 3× per library

### Success Criteria
- Measure reduction in failed generations
- Evaluate feature discovery completeness

---

## Experiment 6: PGO and Source Analysis Exploration

**Objective:** Leverage Profile-Guided Optimization (PGO) data and static analysis to inform test generation.

### Fixed Variables
- Model: `oca/gpt5`
- Libraries: Genesis benchmark set (with PGO profiles available)

### Independent Variable: Context Enrichment

| Config ID | Enrichment | Description |
|-----------|------------|-------------|
| `E1-none` | No enrichment | Control |
| `E2-pgo` | PGO profile injection | Provide hot method list to LLM |
| `E3-source` | Library source in context | Add decompiled/source JAR contents |
| `E4-callgraph` | Static call graph | Provide method call relationships |
| `E5-combined` | PGO + source + callgraph | Full enrichment |

### PGO Data Format
```json
{
  "hot_methods": [
    {"class": "com.example.Foo", "method": "bar", "invocations": 10000},
    ...
  ],
  "reflection_targets": [
    {"class": "com.example.Config", "accessed_fields": ["host", "port"]}
  ]
}
```

### Procedure
1. Generate PGO profiles from existing tests (or production traces)
2. Implement context injection in `ai_workflows/context_enrichment/`
3. Create enrichment-aware prompts
4. Run each configuration 3× per library

### Additional Metrics
- `pgo_methods_covered`: Hot methods exercised by generated tests
- `reflection_coverage`: PGO-identified reflection targets covered

### Success Criteria
- Measure correlation between PGO guidance and metadata generation
- Quantify diminishing returns of context size

---

## Benchmark Library Selection

### Primary Benchmark Set (Genesis)
From [`benchmarks/benchmark_suite.json`](../benchmarks/benchmark_suite.json):
- `com.hazelcast:hazelcast:5.5.0` — Distributed computing, heavy reflection
- `org.hibernate:hibernate-core:5.6.14.Final` — ORM, proxies, serialization
- `io.jsonwebtoken:jjwt-jackson:0.11.5` — Crypto, smaller scope

### Extended Benchmark Set (for Experiments 5-6)
Add libraries with known dynamic feature usage:
- `com.fasterxml.jackson.core:jackson-databind:2.17.0` — Serialization-heavy
- `org.springframework:spring-context:6.1.0` — Proxy/AOP-heavy
- `io.netty:netty-all:4.1.100.Final` — JNI, native resources

---

## Execution Infrastructure

### Run Configuration
```bash
# Example: Run Experiment 1, Variant P2, Library 1, Run 1
python benchmarks/benchmark_runner.py \
  --strategy exp1-P2-verbose \
  --library "com.hazelcast:hazelcast:5.5.0" \
  --output-dir results/exp1/P2/hazelcast/run1/
```

### Results Directory Structure
```
results/
├── exp1-prompts/
│   ├── P1-baseline/
│   │   ├── hazelcast/run{1,2,3}/
│   │   ├── hibernate/run{1,2,3}/
│   │   └── jjwt/run{1,2,3}/
│   ├── P2-verbose/
│   └── ...
├── exp2-agents/
├── exp3-models/
├── exp4-mcps/
├── exp5-research/
└── exp6-enrichment/
```

### Aggregation Script
Create `benchmarks/aggregate_results.py` to:
1. Parse all `run_metrics_output.json` files
2. Compute mean, stddev, min, max per configuration
3. Export CSV/JSON for visualization

---

## Timeline and Prioritization

| Priority | Experiment | Estimated Runs | Rationale |
|----------|------------|----------------|-----------|
| 1 | Exp 3 (Models) | 63 | Core economic question |
| 2 | Exp 1 (Prompts) | 45 | Low-cost, high-insight |
| 3 | Exp 4 (MCPs) | 54 | Validates MCP investment |
| 4 | Exp 2 (Agents) | 81 | Infrastructure comparison (9 variants × 3 libs × 3 runs) |
| 5 | Exp 6 (PGO) | 45 | Research exploration |
| 6 | Exp 5 (Research) | 36 | Advanced methodology |

**Total Estimated Runs:** ~324 (assuming 3 libraries × 3 runs × variable variants)

---

## Deliverables

1. **Raw Results:** JSON files per run conforming to schema
2. **Aggregated Data:** CSV exports with statistical summaries
3. **Visualizations:**
   - Box plots: metrics by experiment variable
   - Scatter plots: coverage vs cost
   - Pareto charts: multi-objective optimization
4. **Presentation Slides:** Section 8 content with key findings
