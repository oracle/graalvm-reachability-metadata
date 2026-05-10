# Post-generation intervention report

Library: org.apache.groovy:groovy-templates:4.0.24
Stage: metadata_fix_failed

## Summary

The generated `groovy-templates` tests still fail in the native test stage. The failures are metadata-related, so no generated tests were removed and no metadata files were modified.

The requested Codex metadata-fix log was not present at `logs/org.apache.groovy:groovy-templates:4.0.24/metadata-fix/codex.log` in this worktree. The available Gradle outputs show that native-image can build the test executable, but the executable fails at runtime because Groovy's reflective/metaclass initialization and dynamic dispatch are missing required reflection metadata.

## Root cause by failure

All seven generated tests share the same underlying native runtime metadata problem:

- `simpleTemplateEngineEvaluatesBindingsExpressionsAndScriptlets()`
- `simpleTemplateEngineCreatesTemplatesFromCharsetEncodedFiles()`
- `streamingTemplateEngineWritesTemplatesCreatedFromReaders()`
- `gStringTemplateEngineUsesModelValuesAndOutputWriter()`
- `xmlTemplateEngineEvaluatesGspScriptletsAndExpressions()`
- `markupTemplateEngineBuildsEscapedMarkupWithConfiguration()`
- `markupTemplateEngineComposesLayoutsFragmentsAndIncludedText()`

The Gradle failure excerpt reports `NoClassDefFoundError: Could not initialize class groovy.lang.GroovySystem` while JUnit constructs `Groovy_templatesTest`. A traced native run shows the immediate missing registration:

```text
org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively access the 'java.util.ResourceBundle'.
```

The stack goes through Groovy's `CachedSAMClass`, `MetaClassRegistryImpl`, and `GroovySystem.<clinit>()`, which means Groovy fails during metaclass/extension-method initialization before the individual template assertions can run. Other native runtime outputs also show Groovy dynamic dispatch failures such as missing zero-argument `Closure.call()` dispatch and missing static dispatch for `java.nio.file.Files.createTempDirectory(String)`, which are consistent with incomplete reflection/introspection metadata for Groovy's runtime and the APIs it inspects.

## Metadata still missing

At minimum, the native run still needs reflection metadata for `java.util.ResourceBundle` sufficient for Groovy's SAM/metaclass scanning. Additional metadata may also be needed for Groovy closure dispatch and static method discovery used by the generated tests, as indicated by the `MissingMethodException` failures for generated closure `call()` methods and `java.nio.file.Files.createTempDirectory(String)`.

Because the Codex log was unavailable, I could not confirm the exact point where Codex stopped. The Gradle trace indicates the metadata fix was only partial: it produced enough metadata for image generation to complete, but not enough for Groovy's runtime metaclass initialization and dynamic template execution in the native executable.

## Why the generated support should be preserved

The remaining generated tests exercise the core public APIs of `groovy-templates`: `SimpleTemplateEngine`, `StreamingTemplateEngine`, `GStringTemplateEngine`, `XmlTemplateEngine`, and `MarkupTemplateEngine`, including file-backed templates, bindings, GSP-style expressions, layout inclusion, and escaped markup. These are meaningful coverage for this artifact and the observed failures expose real reachability metadata gaps rather than unsupported test behavior. Preserving the tests keeps that coverage available for a future metadata-only fix instead of masking the missing Groovy runtime metadata.
