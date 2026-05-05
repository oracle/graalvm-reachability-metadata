Role: You are an expert JVM, Native Image, and library compatibility analyst.

Analyze whether the uncovered dynamic-access call sites can be reached in the current Forge setup after the PGO near-call diagnostic could not produce actionable guidance.

Do not edit files. Return only one JSON object and no Markdown.

Current Forge runtime environment:
{runtime_environment_summary}

Current test runtime classpath. This is the current state, but it is changeable by editing the library test module `build.gradle` when that would make the call site reachable:
{test_runtime_classpath}

Current library test module build.gradle:
```groovy
{build_gradle_contents}
```

Library:
- Coordinate: `{library}`
- Active dynamic-access class: `{active_class_name}`
- Source file: `{active_class_source_file}`

Uncovered dynamic-access call sites:
{uncovered_dynamic_access_calls}

PGO diagnostic result:
{pgo_failure_reason}

Source context overview:
{source_context_overview}

Classify each uncovered call site conservatively:
- Mark `reachable` as `false` only when the call site is not reachable in this setup even after reasonable test changes, including adding test dependencies, JVM args, or Native Image build args.
- Good `false` reasons include OS-specific branches that cannot execute on this OS, JDK-version guards that exclude this JDK, unavailable native transports/providers for this environment, or library code paths gated by artifacts that cannot work in this setup.
- If the call site can likely be reached by adding a dependency, changing test inputs, changing `build.gradle`, or exercising a different public API, mark `reachable` as `true` and describe the needed change in `requiredChanges`.
- If uncertain, mark `reachable` as `true`.

Return exactly this JSON shape:
{{
  "summary": "short explanation",
  "callSites": [
    {{
      "metadataType": "same value as the report",
      "trackedApi": "same value as the report",
      "frame": "same value as the report",
      "line": 42,
      "reachable": false,
      "reason": "why this call site is or is not reachable in the current setup",
      "requiredChanges": "test/build.gradle changes that would be needed if reachable, otherwise empty",
      "confidence": "high"
    }}
  ]
}}
