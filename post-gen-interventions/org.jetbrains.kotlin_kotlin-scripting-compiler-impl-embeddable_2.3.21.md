# Post-generation intervention report

Library: org.jetbrains.kotlin:kotlin-scripting-compiler-impl-embeddable:2.3.21
Stage: metadata_fix_failed

## Summary

The remaining failure is in `RefineCompilationConfigurationKtTest.collectsAcceptedScriptAnnotationUsingContextClassLoader()` during `nativeTest`. The test creates a `.kts` file annotated with `RefineCompilationConfigurationTestAnnotation`, calls `getScriptCollectedData(...)`, and expects the Kotlin scripting refinement path to collect that annotation. In the native-image run, the collected annotation list is empty and the custom context class loader never loads the annotation class.

## Root cause

This failure is metadata-related. The Codex metadata-fix log shows that the failure is not an explicit `Missing*RegistrationError`; instead, Kotlin scripting/compiler setup silently loses part of its runtime service bootstrap in the native image. Codex instrumentation found that the native `KotlinCoreEnvironment` does not have the scripting project service needed by the refinement path (`ScriptDefinitionProvider.getInstance(environment.project)` was `null`), so `.kts` script handling does not reach the annotation collection/refinement path.

Codex partially addressed the suspected missing metadata by adding service resources and reflective constructors for the scripting compiler plugin bootstrap, including service descriptors such as `META-INF/services/org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar`, `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`, and `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor`, plus registrar/processor constructors. The clean rerun still failed with the same empty-annotation assertion, so additional metadata is still missing around the Kotlin scripting compiler plugin/project-service registration path, likely involving the concrete scripting service implementations such as `CliScriptDefinitionProvider` and `CliScriptConfigurationsProvider` and/or the dynamic registration path that installs them into `KotlinCoreEnvironment`.

Codex could not finish the fix because the native-image failure does not report a concrete missing registration entry. The broken behavior is a silent service/extension bootstrap failure inside Kotlin/IntelliJ compiler infrastructure rather than a thrown reachability exception with suggested JSON, so the remaining metadata cannot be derived mechanically from the Gradle output.

## Action taken

No generated test was removed. The failure is classified as metadata-related, and metadata files were not modified during this intervention.

## Why preserve the remaining generated support

The generated support should be preserved because it exercises real Kotlin scripting APIs for this artifact: classpath script-template loading/discovery and script annotation collection during refinement. The other generated tests completed successfully, and the failing test exposed a genuine native-image reachability gap in Kotlin scripting compiler service initialization. Removing the test would hide unresolved metadata needed for script annotation refinement rather than eliminating a bad or unsupported test case.
