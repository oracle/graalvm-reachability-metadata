# Post-generation intervention report

Library: org.apache.pulsar:pulsar-client:4.2.1
Stage: metadata_fix_failed

## Summary

The generated `Pulsar_clientTest` suite is valid, but the native test run fails because the generated reachability metadata is incomplete for Pulsar's shaded dependencies. The failures are metadata-related rather than test bugs or unsupported platform behavior, so no generated tests were removed.

The JVM test report in the Codex log shows all seven tests pass on the JVM. Codex then generated and adjusted metadata, which fixed the shaded Avro and authentication failures in later native runs, but the final native execution still fails in shaded Netty initialization with `NoClassDefFoundError: Could not initialize class org.apache.pulsar.shade.io.netty.util.internal.PlatformDependent`.

## Failure root causes

- `transactionsExposeValueObjectsAndRequireExplicitClientEnablement()`
  - Initial failure: `ExceptionInInitializerError` from shaded Netty/JCTools queue initialization, caused by `NoSuchFieldException: producerIndex`.
  - Final remaining failure after Codex metadata generation: `NoClassDefFoundError` for `org.apache.pulsar.shade.io.netty.util.internal.PlatformDependent`.
  - Root cause: metadata-related. Shaded Netty/JCTools queue internals still need additional field/method reflection metadata, including linked MPSC queue field registrations beyond the partial generated/ported Netty metadata.

- `structKeyValueAndGenericSchemasRoundTripRecords()`
  - Initial failure: shaded Avro `AvroRuntimeException: Unable to load a functional FieldAccess class!` during `ReflectData` initialization.
  - Root cause: metadata-related. The POJO Avro schema path needs shaded Avro reflection/field-access metadata. Codex generated metadata that made this test pass in later native runs, so the test should be preserved.

- `policiesRangesAndMessageIdsUsePublicValueObjects()`
  - Initial failure: shaded Netty `Unpooled` initialization failed because `ResourceLeakDetector.addExclusions` could not find `toLeakAwareBuffer` on `org.apache.pulsar.shade.io.netty.buffer.AbstractByteBufAllocator`.
  - Final remaining failure: `PlatformDependent` initialization failure.
  - Root cause: metadata-related. Netty's leak-detector exclusion logic and buffer initialization depend on reflective method/field visibility for shaded Netty classes.

- `configuredClientBuildersFailFastAgainstUnavailableBroker()`
  - Failure: `NoClassDefFoundError` for shaded JCTools `MpscUnboundedArrayQueue`, reached through shaded Netty `NioEventLoop`/`PlatformDependent` initialization.
  - Root cause: metadata-related. The client-builder path initializes shaded Netty event loops and exposes the same incomplete shaded Netty/JCTools metadata.

- `primitiveTemporalAndPayloadSchemasRoundTripValues()`
  - Failure: `NoClassDefFoundError: Could not initialize class org.apache.pulsar.shade.io.netty.buffer.Unpooled`, reached from `MessagePayloadFactoryImpl.wrap`.
  - Root cause: metadata-related. The primitive schema assertions are fine; only the payload wrapping subpath initializes shaded Netty buffer classes whose reflection metadata is incomplete.

- `authenticationFactoriesProduceUsableAuthenticationData()`
  - Initial failure: `ClassNotFoundException: org.apache.pulsar.client.impl.auth.AuthenticationToken` from `AuthenticationUtil.create`/`Class.forName`.
  - Root cause: metadata-related. The token plugin class needs reachability/reflective class registration. Codex generated metadata that made this test pass in later native runs, so the test should be preserved.

## Metadata still missing

Codex partially fixed the metadata by running metadata generation and merging additional shaded Netty entries; `checkMetadataFiles` passed afterward. However, native execution still fails because shaded Netty's `PlatformDependent` static initializer collapses the underlying missing reflective access into `NoClassDefFoundError` instead of emitting a concrete `Missing*RegistrationError` with a suggested JSON snippet.

The remaining gap is in Pulsar's shaded Netty/JCTools metadata, particularly registrations used by:

- `org.apache.pulsar.shade.io.netty.util.internal.PlatformDependent` and `PlatformDependent0`;
- `org.apache.pulsar.shade.io.netty.buffer.AbstractByteBufAllocator` leak-detector exclusion methods such as `toLeakAwareBuffer`;
- shaded JCTools MPSC queue classes, including linked-array/unbounded queue producer/consumer fields such as `producerIndex` and related queue offset fields.

Codex could not finish resolving this because the later failures no longer produced precise missing-registration diagnostics. The native image only reported the already-failed static initializer (`Could not initialize class PlatformDependent`), making further changes require source-level Netty/JCTools analysis rather than straightforward metadata-fix iteration.

## Why the generated support should be preserved

The generated tests exercise meaningful public Pulsar client functionality: schema encoding/decoding, generic and Avro schema handling, authentication factory paths, public policy/value objects, client builder configuration, and transaction value objects. The Codex log shows the JVM version of the suite passes, and the native failures are concentrated in missing reachability metadata for shaded dependencies. Keeping the generated support preserves coverage for real Pulsar native-image usage and provides concrete reproducers for the remaining shaded Netty/JCTools metadata gaps.
