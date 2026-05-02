# Post-generation Intervention Report

Library: org.apache.groovy:groovy:4.0.22
Stage: metadata_fix_failed

## Summary

The metadata-fix run resolved the earlier bootstrap/metadata progress far enough to build and run the native test image, but the generated Groovy test suite still had eight native-runtime failures. Seven failures were not ordinary reachability-metadata gaps: Groovy attempted to define optimized call-site/helper classes at run time, or hit native-image-incompatible dynamic Groovy dispatch behavior. Those generated tests were removed.

One generated test failure is still metadata-related and was preserved: `observableCollectionsPublishMutationEvents()` fails because the native image is missing dynamic-proxy reflection metadata for `java.beans.PropertyChangeListener`.

After removing the non-metadata failures, `./gradlew test -Pcoordinates=org.apache.groovy:groovy:4.0.22 --stacktrace` now runs two tests: `stringExtensionsNormalizePaddingAndTokens()` passes and `observableCollectionsPublishMutationEvents()` fails only with the remaining proxy metadata gap.

## Failure classification

| Test | Root cause | Action |
| --- | --- | --- |
| `gStringSupportsLazyInterpolationAndWritableOutput()` | Native Image blocks Groovy runtime class generation for an optimized call-site helper, reported as `UnsupportedFeatureError` while defining `java_util_concurrent_atomic_AtomicInteger$get`. This is a native-image/runtime code-generation limitation, not a missing metadata entry. | Removed. |
| `closuresSupportCurryingCompositionMemoizationAndTrampolining()` | Failed with a Groovy runtime `NullPointerException` in `CurriedClosure.<init>`, not a GraalVM missing-registration error. | Removed. |
| `rangesAndCollectionExtensionsProvideDeterministicTransformations()` | Failed with `MissingMethodException` for a generated closure call signature, not a missing metadata entry. | Removed. |
| `tupleAndExpandoExposeTypedAndDynamicState()` | Native Image blocks Groovy call-site helper generation, reported as `UnsupportedFeatureError` while defining `groovy.lang.Tuple$tuple`. | Removed. |
| `configObjectFlattensMergesAndRendersNestedConfiguration()` | Native Image blocks Groovy call-site helper generation, reported as `UnsupportedFeatureError` while defining `groovy.util.ConfigObject$flatten`. | Removed. |
| `invokerHelperCreatesScriptsWithBindings()` | Native Image blocks Groovy call-site helper generation, reported as `UnsupportedFeatureError` while defining `org.codehaus.groovy.runtime.InvokerHelper$createScript`. | Removed, along with its generated `GreetingScript` helper. |
| `groovyShellEvaluatesRuntimeScriptsOrFailsForNativeDynamicClassLoading()` | `GroovyShell.evaluate` requires runtime class definition (`groovy.lang.GroovyShell$evaluate`), which Native Image blocks by default. The test also failed to call the TCK helper through Groovy dynamic dispatch. This is not a normal metadata-only fix. | Removed. |
| `observableCollectionsPublishMutationEvents()` | Metadata-related. The failure is `MissingReflectionRegistrationError` for reflective access to a dynamic proxy implementing `java.beans.PropertyChangeListener`. The missing entry suggested by Native Image is a reflection entry with `type.proxy = ["java.beans.PropertyChangeListener"]`. | Preserved; metadata was not modified in this intervention. |

## Remaining metadata gap

The remaining generated test needs proxy reflection metadata for:

```json
{
  "type": {
    "proxy": [
      "java.beans.PropertyChangeListener"
    ]
  }
}
```

Codex did not finish the metadata fix because it focused on broad Groovy reflection/DGM entries and then stopped after classifying the larger runtime class-definition failures as outside normal `reachability-metadata.json` scope. It noted a remaining dynamic-proxy registration gap for `java.beans.PropertyChangeListener`, but the workflow ended before that metadata entry was added.

## Why preserve the remaining support

The remaining generated support should still be preserved because it contains useful, native-image-compatible coverage for Apache Groovy 4.0.22:

- `stringExtensionsNormalizePaddingAndTokens()` already passes in the native image and validates stable Groovy extension-method behavior.
- `observableCollectionsPublishMutationEvents()` exercises real Groovy observable collection behavior and now fails for a concrete, metadata-related dynamic-proxy registration gap rather than an unsupported runtime class-generation feature.
- The Codex-generated metadata and build configuration allow the test image to compile and narrow the remaining failure to a single actionable metadata item without depending on unsupported Groovy runtime script/class generation.
