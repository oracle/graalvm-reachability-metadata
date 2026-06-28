# Post-generation intervention report

Library: org.apache.groovy:groovy-macro:4.0.24
Stage: metadata_fix_failed

## Summary

The generated `Groovy_macroTest.groovy` test fixture failed all 8 native-image tests at runtime. The failure shown in the Gradle output is `BootstrapMethodError` caused by a `NullPointerException` in `org.codehaus.groovy.vmplugin.v8.IndyInterface.make(...)` while Groovy's invokedynamic bootstrap creates/adapts call sites.

## Root cause classification

This is not a reachability-metadata failure. The Codex metadata-fix log shows that after genuine metadata gaps were addressed, the test continued through Groovy's dynamic runtime paths and exposed native-image limitations:

- Groovy indy bootstrap reached `MutableCallSite.setTarget(...)`, which native-image reports as unsupported via `UnsupportedFeatureError: Unsupported method java.lang.invoke.MethodHandleNatives.setCallSiteTargetNormal(CallSite, MethodHandle) is reachable`.
- An exploratory non-indy path then reached Groovy runtime call-site bytecode generation and failed in `RuntimeClassLoading.defineClass(...)` / `ClassLoaderForClassArtifacts.defineClassAndGetConstructor(...)`, i.e. runtime class definition/loading.

Per the intervention rules, runtime bytecode generation, runtime class definition/loading, and invokedynamic call-site behavior are unsupported native-image behavior for generated tests. Therefore the generated test source was removed instead of attempting additional metadata changes.

## Removed generated test support

Removed:

- `tests/src/org.apache.groovy/groovy-macro/4.0.24/src/test/groovy/org_apache_groovy/groovy_macro/Groovy_macroTest.groovy`

This file contained all 8 failing tests:

- `macroBuilderCreatesExpressionBlocksClassesAndSubstitutesPlaceholders`
- `compileTimeMacroExtensionProducesAstNodeInsteadOfRuntimeCall`
- `compileTimeMacroExtensionCanReturnClosureBodyAsBlock`
- `astMatcherMatchesWildcardsPlaceholdersAndTokenConstraints`
- `astMatcherFindsNestedMatchesAndTreeContextCarriesTraversalState`
- `macroClassAnonymousBodyProducesClassNode`
- `macroContextExposesCompilationSourceAndCallObjects`
- `macroBuilderReturnsRequestedAstNodeShapesForControlFlow`

## Remaining generated support

The remaining generated support should still be preserved because Codex identified real, metadata-related support for Groovy startup/runtime discovery before the native-image-only dynamic runtime blocker was reached. In particular, the generated metadata records access to `META-INF/dgminfo` and reflective construction of `org.codehaus.groovy.vmplugin.v16.Java16`, both of which are independent of the deleted test's unsupported invokedynamic/runtime class-definition paths.

## Verification

After deleting the generated failing test source, the targeted command completed successfully:

```text
./gradlew clean test -Pcoordinates=org.apache.groovy:groovy-macro:4.0.24 --stacktrace
```
