# Post-generation intervention report

Library: org.apache.groovy:groovy:4.0.22

Stage: `metadata_fix_failed`

## Summary

The generated Groovy tests compile and pass on the JVM, but the native test run fails. The Gradle excerpt shows all 15 JUnit tests failing immediately in the native executable with `NoClassDefFoundError: Could not initialize class groovy.lang.GroovySystem`. This is a metadata-related failure, not a test bug or an unsupported platform feature, so no generated tests were removed.

## Root cause

Groovy initializes its meta-class and Default Groovy Methods machinery through dynamic class lookup and reflective access. The native image does not currently contain enough reachability metadata for that bootstrap path.

The Codex metadata-fix log confirms this diagnosis:

- The existing metadata for `org.apache.groovy:groovy:4.0.22` started effectively empty.
- Exact native metadata tracing exited with code `172`, indicating missing metadata rather than an ordinary assertion failure.
- The first trace reported missing reflection entries reached from `org.codehaus.groovy.reflection.GeneratedMetaMethod$DgmMethodRecord`, including `java.util.BitSet` plus other Groovy/JDK types.
- A second trace exposed another batch of missing reflection entries, still under the same Groovy DGM bootstrap condition.
- After partial progress, native failures still involved unresolved dynamic Groovy access, including `org.codehaus.groovy.runtime.dgm$NNN` helper classes and missing reflective constructors/static method access for Groovy and JDK types.

Codex could not complete the fix because Groovy's DGM/bootstrap surface is broad and iterative: each exact native trace only revealed the next partial batch of required metadata, and Groovy ships many generated `org.codehaus.groovy.runtime.dgm$NNN` helper classes. The JVM agent route was also blocked in the logged attempt because the active GraalVM home did not expose `libnative-image-agent.so`, so Codex had to rely on slower native exact-trace iterations.

## Failure classification

All listed generated test failures are metadata-related. They share the same native Groovy runtime initialization problem rather than independent test defects:

- `bindingStoresScriptVariablesAndReportsMissingEntries`
- `gStringExposesSegmentsValuesWritableAndPatternConversion`
- `closuresSupportDelegationCurryingCompositionAndMemoization`
- `trampolineClosureComputesRecursiveResultsWithoutGrowingStack`
- `expandoStoresDynamicPropertiesAndInvokesClosureBackedMethods`
- `rangesExposeBoundsIterationSteppingAndContainment`
- `tuplesProvideTypedAccessorsComparableOrderingAndImmutableViews`
- `nodeBuilderCreatesNavigableNodeTrees`
- `configObjectFlattensMergesAndWritesNestedConfiguration`
- `defaultedCollectionsCreateValuesFromClosures`
- `groovyCollectionsCalculateCombinationsTransposesSubsequencesAndUnions`
- `observableListPublishesElementAndSizeChangeEvents`
- `fileExtensionMethodsReadWriteTraverseAndTransformText`
- `groovyShellEvaluatesScriptsWithBindingWhenDynamicCompilationIsAvailable`
- `configSlurperParsesEnvironmentAwareScriptsWhenDynamicCompilationIsAvailable`

## Why the generated support should be preserved

The generated support should remain because it exercises real, representative Groovy APIs and already passes on the JVM. The native failures reveal incomplete reachability metadata for Groovy's dynamic runtime and generated DGM method infrastructure. Removing the tests would hide valid metadata gaps and reduce coverage for core Groovy behavior such as bindings, closures, ranges, tuples, node/config utilities, collection helpers, observable lists, file extension methods, and guarded dynamic compilation paths.

No metadata files were modified as part of this intervention.
