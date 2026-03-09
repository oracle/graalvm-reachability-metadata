---
name: fix-missing-reachability-metadata
description: Fix missing GraalVM reachability metadata for a specific library. Use when the user asks to fix or add metadata because required metadata is missing and tests or native-image runs fail with missing metadata errors.
---

# Fix missing reachability metadata entries in the reachability-metadata.json

## Overview

Run a reproduce-fix-verify loop for missing metadata errors. Keep running the target test until it passes with no new missing metadata entries.

Library coordinates should be provided in the prompt in the format `group:artifact:version`. If you are not sure what the coordinates are, ask the user.

## Workflow

1. Reproduce the failure:
   - Run `./gradlew test -Pcoordinates=<coordinates>` from repository root.
2. Capture the missing metadata from the error message:
   - Locate any `Missing*RegistrationError` (e.g. `MissingReflectionRegistrationError`, `MissingResourceRegistrationError`, or any other variant).
   - Copy the suggested JSON entry for the missing type into the corresponding section of the reachability-metadata.
3. Choose the target file and insert the entry:
   - Write to `metadata/<library-specific-metadata-directory>/reachability-metadata.json`.
   - Keep valid JSON and avoid duplicating an existing equivalent entry. If the type already exists but a method or field is missing, add only that method or field.
4. Add the missing `condition` field:
   - Infer it from the error stack trace.
   - Set the `condition` field to `{ "typeReached": "<class>" }`, where `<class>` is the first class on the stack trace whose package shares the leading namespace with the tested library's package.
   - Package overlap can be partial, full package equality is not required.
   - Example: missing type `org.hibernate.id...` and the class from a stack trace `org.hibernate.resource...` is a valid match.

5. Verify and iterate:
   - Run `./gradlew test -Pcoordinates=<coordinates>` again.
   - If another missing entry appears, repeat from step 2.
   - Finish only when the test run succeeds.

## Error Patterns

Use this reference when deriving the missing entry and condition.

### Missing Registration Entry Pattern

Example error shape:

```text
Caused by: org.graalvm.nativeimage.MissingReflectionRegistrationError:
Cannot reflectively invoke constructor 'public org.hibernate.id.enhanced.SequenceStyleGenerator()'
...
add the following to the 'reflection' section of reachability-metadata.json:
{
  "type": "org.hibernate.id.enhanced.SequenceStyleGenerator",
  "methods": [{ "name": "<init>", "parameterTypes": [] }]
}
...
at org.hibernate.boot.model.internal.GeneratorBinder.instantiateGeneratorViaDefaultConstructor(...)
at org.hibernate.resource.beans.internal.Helper$2.produceBeanInstance(...)
```

### Condition Inference

For missing type `org.hibernate.id.enhanced.SequenceStyleGenerator`, a valid condition class is:

`org.hibernate.boot.model.internal.GeneratorBinder`

Reason: both share the leading namespace `org.hibernate`, which is sufficient for this workflow.

## Environment Troubleshooting

- If failures look unrelated or tooling looks inconsistent, verify both `JAVA_HOME` and `GRAALVM_HOME` point to a compatible GraalVM JDK distribution.
- If they are not set to GraalVM, ask the user to provide the correct GraalVM distribution path and retry.