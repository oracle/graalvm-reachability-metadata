# Post-generation intervention report

Library: org.apache.maven:maven-project:3.0-alpha-1
Stage: metadata_fix_failed

## Summary

The native test run failed because generated support for `org.apache.maven:maven-project:3.0-alpha-1` still needs reachability metadata for Maven/Plexus runtime wiring. The failures are metadata-related, so no generated tests were removed.

## Failure root causes

1. `DefaultMavenProjectBuilderTest.buildStandaloneSuperProjectLoadsBundledSuperPomResource()`
   - Failure: `ComponentLookupException` while looking up `org.apache.maven.project.MavenProjectBuilder` from `DefaultPlexusContainer`.
   - Root cause: metadata-related. Plexus component descriptors and implementations from `META-INF/plexus/components.xml` are discovered, but some component implementation classes and injectable fields are not fully materialized in the native image. Codex partially fixed the `maven-project` layer, after which the remaining lookup chain moved into transitive `maven-compat` components. The last Codex run stopped with a missing descriptor for `org.apache.maven.artifact.factory.ArtifactFactory`, needed through `DefaultMavenProjectBuilder -> DefaultProfileAdvisor -> DefaultMavenTools`.

2. `ModelDefaultsInjectorAnonymous1Test.superPomSuppliesModelDefaults()`
   - Failure: `NullPointerException` when reading default build values from the super POM model.
   - Root cause: metadata-related. The Maven super POM resource `org/apache/maven/project/pom-4.0.0.xml` was not available to the native image, so `DefaultProjectBuilder.getSuperModel()` did not supply the expected build defaults. Codex identified and added the corresponding resource metadata during its partial fix.

3. `ModelInterpolatorAnonymous1Test.interpolateModelAppliesProvidedProperties()`
   - Failure: Woodstox reported `No JDK implementation wrapper class available` and listed missing `com.ctc.wstx.compat.Jdk14Impl`, `Jdk13Impl`, and `Jdk12Impl` classes.
   - Root cause: metadata-related. Woodstox chooses those compatibility implementations reflectively, and the native image lacked reflection metadata for their constructors. Codex added these entries during its partial fix, and this test subsequently passed.

## Why the generated support should be preserved

The failing tests exercise real Maven 3.0-alpha-1 behavior: Plexus component descriptor loading, super POM resource loading, and XML model interpolation through Woodstox. The Gradle output and Codex log show metadata gaps rather than a test bug, unsupported platform feature, or native-image limitation. The generated support also includes several passing smoke tests for Maven roles, and Codex's partial fixes moved two of the three original failures to passing. Preserving the tests keeps coverage for valid native-image support and documents the remaining metadata work, especially the unfinished `maven-compat:3.0-alpha-1` component metadata needed by the MavenProjectBuilder dependency chain.
