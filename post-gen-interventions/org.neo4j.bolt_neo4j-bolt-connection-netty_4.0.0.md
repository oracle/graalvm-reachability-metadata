# Post-generation intervention report

Library: org.neo4j.bolt:neo4j-bolt-connection-netty:4.0.0

Stage: `metadata_fix_failed`

## Summary

The native test image built successfully, but five generated integration-style Bolt protocol tests failed at native-image runtime. The common root cause was a `NullPointerException` in Netty internals:

`io.netty.util.internal.PlatformDependent0.safeConstructPutInt` -> `ReferenceCountUpdater.setInitialValue` -> `io.netty.buffer.AdaptivePoolingAllocator$Chunk.<init>`

This surfaced as `BoltServiceUnavailableException: Unable to write Bolt handshake` for connection tests, and as an unexpected `NullPointerException` root cause for the unsupported-protocol assertion test.

The requested Codex metadata-fix log path, `logs/org.neo4j.bolt:neo4j-bolt-connection-netty:4.0.0/metadata-fix/codex.log`, was not present in this worktree. The Gradle output was sufficient to classify the failures because native-image compilation completed and the failures were runtime allocator failures inside Netty rather than missing reflection, resource, proxy, serialization, or class-initialization metadata diagnostics.

## Root cause by failed test

- `providerConnectsToBoltServerAndHandlesQueryTransactionAuthAndTelemetryMessages()` failed while opening a real Netty Bolt connection to the in-process test server. The root cause was the Netty `AdaptivePoolingAllocator` `NullPointerException`, not missing reachability metadata.
- `connectionRunsAutoCommitTransactionAndDiscardsResultStream()` failed for the same Netty allocator `NullPointerException` during handshake/write, not missing metadata.
- `connectionRouteDiscoversClusterComposition()` failed for the same Netty allocator `NullPointerException` during handshake/write, not missing metadata.
- `providerConnectionChecksUseHandshakeAuthenticationAndMinimumVersion()` failed for the same Netty allocator `NullPointerException` during handshake/write, not missing metadata.
- `providerReportsUnsupportedBoltProtocolNegotiation()` expected a `BoltClientException`, but the connection path failed earlier with the same Netty allocator `NullPointerException`; this is not a metadata failure.

## Intervention

Removed the generated tests that required live Netty client/server traffic and the generated test-only Bolt server/PackStream support used only by those tests. Metadata files were not modified.

## Preserved generated support

The remaining `bootstrapFactoryCreatesReusableNettyBootstraps()` test should be preserved because it exercises useful supported native-image coverage for `neo4j-bolt-connection-netty`: creating Netty bootstraps, creating a `NettyBoltConnectionProviderFactory` provider with a supplied `EventLoopGroup`, closing the provider, and verifying lifecycle behavior without entering the unsupported Netty adaptive allocator path that broke the integration-style connection tests.

Validation after the intervention:

```text
./gradlew test -Pcoordinates=org.neo4j.bolt:neo4j-bolt-connection-netty:4.0.0 --stacktrace
BUILD SUCCESSFUL
1 tests found, 1 tests successful, 0 tests failed
```
