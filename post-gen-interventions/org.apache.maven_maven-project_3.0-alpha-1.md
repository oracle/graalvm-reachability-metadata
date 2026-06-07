# Post-generation intervention report

Library: org.apache.maven:maven-project:3.0-alpha-1
Stage: metadata_fix_failed

## Summary

The remaining failure is in native test execution for `DefaultMavenProjectBuilderTest.buildStandaloneSuperProjectLoadsBundledSuperPomResource`. The JVM tests pass, but the native executable fails while `DefaultPlexusContainer` constructs `org.apache.maven.project.DefaultMavenProjectBuilder` from Plexus component descriptors.

## Root cause

This is metadata-related, so the generated test was not removed.

The failure is caused by incomplete reachability/resource support for the Maven/Plexus component graph. Plexus successfully starts resolving `MavenProjectBuilder`, but dependency injection then fails through this chain:

- `DefaultMavenProjectBuilder.profileAdvisor` requires `org.apache.maven.profiles.build.ProfileAdvisor`
- `DefaultProfileAdvisor.mavenTools` requires `org.apache.maven.MavenTools`
- `DefaultMavenTools.artifactFactory` requires `org.apache.maven.artifact.factory.ArtifactFactory`
- Plexus finally reports that the component descriptor for `ArtifactFactory` cannot be found in the component repository

That points to missing native-image metadata for the broader Plexus descriptor-discovery and component-instantiation path, especially Maven component descriptors such as `META-INF/plexus/components.xml` from dependent Maven artifacts and the reflected implementation classes/fields they declare. Codex partially progressed the metadata repair, but the log shows it was still chasing the expanding component graph when verification failed. It also attempted the repository `generateMetadata` path, but that could not run in this environment because the GraalVM installation used by the workflow did not provide `libnative-image-agent.so`.

## Why preserve the remaining generated support

The generated support should be preserved because the failing test exercises real `maven-project` behavior: loading Maven's bundled super POM and constructing `DefaultMavenProjectBuilder` through the same Plexus container mechanism used by Maven 3.0-alpha-1. The other generated tests pass in both JVM and native execution, and this remaining failure identifies concrete missing reachability metadata rather than a native-image limitation, unsupported platform feature, or invalid test-only scenario. Removing the test would hide an unresolved metadata gap in Maven/Plexus component discovery instead of documenting the metadata still needed.
