# Post-generation intervention report

Library: org.apache.maven.plugin-testing:maven-plugin-testing-harness:3.3.0
Stage: metadata_fix_failed

## Summary

The generated test suite builds a native image successfully, but the native runtime test
`mojoRuleCanInitializePlexusContainerForMavenHarnessLookups()` fails. The failure occurs while
`MojoRule.setupContainer()` starts the Maven/Plexus/Sisu container and then attempts to look up a
`ComponentConfigurator`.

The Gradle excerpt shows the first failing symptom as a Guice `CreationException`:

- `No implementation for org.codehaus.plexus.MutablePlexusContainer was bound`
- source: `org.codehaus.plexus.DefaultPlexusContainer$ContainerModule.configure(...)`

The Codex metadata-fix log shows that this is metadata-related rather than a test bug or an
unsupported native-image platform feature. Codex added resource/reflection/proxy metadata for the
Plexus/Sisu bootstrap path, after which the failure moved from the missing
`MutablePlexusContainer` binding to a later native-runtime lookup failure:

- `ComponentLookupException: java.util.NoSuchElementException`
- role: `org.codehaus.plexus.component.configurator.ComponentConfigurator`

## Root cause

The failing test exercises dynamic Plexus/Sisu component discovery and Guice-managed component
construction from `MojoRule.setupContainer()`. In native image, that path depends on metadata for
Plexus descriptors, Sisu indexes, annotation proxies, and reflective construction of Maven/Sisu
bootstrap classes.

Codex partially fixed the metadata, but did not fully resolve the Sisu/Plexus binding path. The
remaining missing support is metadata that allows the native image to turn the Plexus component
metadata into visible `ComponentConfigurator` bindings, especially the default configurator role
implemented by Plexus/Sisu classes such as `BasicComponentConfigurator` and
`MapOrientedComponentConfigurator`.

Codex could not finish because the runtime did not produce a direct `Missing*RegistrationError` with
a suggested JSON entry. The Sisu container initially swallowed useful binding diagnostics unless
`-Dsisu.debug=true` was used, and the repository metadata generator path was also blocked by a
missing tracing-agent shared library in the configured GraalVM. As a result, Codex had to infer
metadata manually and stopped with the container created but the `ComponentConfigurator` lookup still
empty.

## Intervention decision

No generated test was removed.

This is a metadata-related failure: the native image starts, eight of the nine generated tests pass,
and the remaining failure is specifically in dynamic Plexus/Sisu discovery and binding. Removing the
failing test would hide a real reachability gap in the Maven plugin-testing harness metadata.

## Why preserve the remaining generated support

The generated support should be preserved because it already validates substantial usable coverage of
`maven-plugin-testing-harness` in native image:

- artifact creation and repository-file behavior through `ArtifactStubFactory`;
- mutable Maven project and artifact test doubles;
- artifact resolver/repository stubs and artifact-handler metadata;
- `MojoRule` field helpers and `@WithoutMojo` bypass behavior;
- resolver expression evaluation;
- `MojoParameters` configuration-node creation;
- `TestResources` fixture copying/assertions; and
- Maven/Plexus logging no-op behavior through `SilentLog`.

The one failing container-initialization test is the only part that still needs additional metadata
work. Keeping it preserves the regression target for completing that metadata instead of discarding
valid generated coverage.
