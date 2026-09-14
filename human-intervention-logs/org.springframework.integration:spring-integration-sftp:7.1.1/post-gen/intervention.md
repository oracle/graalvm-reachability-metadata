# Post-generation intervention

Library: org.springframework.integration:spring-integration-sftp:7.1.1

Stage: `metadata_fix_failed`

## Failure summary

The metadata-fix agent could not apply a change because its workspace was
read-only. The subsequent Gradle run failed in `:nativeTestCompile`, before
the native executable ran any test. Native Image reported that an instance of
`sun.nio.fs.LinuxFileSystemProvider`, initialized through
`java.security.Security.<clinit>` and `java.nio.file.spi.FileSystemProvider`,
was placed in the image heap even though the type is configured for run-time
initialization.

This is an unsupported/native-image build-time class-initialization limitation
of the GraalVM/JDK toolchain, not missing reachability metadata. The captured
successful native run executed all six generated tests successfully, while the
failing run stopped during image construction and identified no individual
test failure. Therefore no generated test was removed: removing an arbitrary
test would not remove the global `Security`/filesystem-provider analysis path.
No metadata files were modified.

## Why the remaining generated support should be preserved

The generated test suite exercises SFTP uploads, downloads, streaming reads,
remote-file operations, outbound gateway listing, and SFTP server event
publication. All six tests passed in the available successful native run, so
they provide valid consumer-relevant coverage. The failure is confined to the
toolchain's image-heap validation and is independent of those test assertions;
the tests should remain available for a compatible GraalVM/native-image
configuration.
