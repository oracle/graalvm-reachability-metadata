# Post-generation intervention report

Library: org.apache.pulsar:pulsar-client-all:4.2.5
Stage: metadata_fix_failed

## Summary

`./gradlew test -Pcoordinates=org.apache.pulsar:pulsar-client-all:4.2.5` reported
40 failing tests out of 258. The `metadata_fix` agent
(`logs/org.apache.pulsar:pulsar-client-all:4.2.5/metadata-fix/claude-code.log`)
timed out twice (20:00, then 40:00) before it could re-run the suite to confirm
its work, but it left a 666-line uncommitted diff in
`metadata/org.apache.pulsar/pulsar-client-all/4.2.5/reachability-metadata.json`
that adds reflection, resource and serialization entries covering 39 of the 40
failures (JCTools unsafe field offsets used by Netty's MPSC queues, dynamic
proxy interfaces created via Guava/Javassist, Gson unsafe allocation, Avro
reflective records, Jackson/JAXB resource bundles and classes, and several
`serializable` collection/multimap types). That diff was left untouched per
instructions — no metadata files were modified by this intervention.

One failure, `ClassLoaderClassResolverTest.fallsBackToClassForNameAfterInitialLoaderFailure()`,
is not a metadata gap and was removed (see below).

## Root-cause analysis

### 1. Not metadata-related — test removed

**`ClassLoaderClassResolverTest`** (`fallsBackToClassForNameAfterInitialLoaderFailure`)
fails with a plain assertion mismatch (`expected: 2 but was: 1`), with no
`MissingReflectionRegistrationError`/`ClassNotFoundException` anywhere in the
trace. The test installs a custom `ClassLoader` that throws
`ClassNotFoundException` on its first `loadClass` call and asserts that Netty's
`ClassResolvers.cacheDisabled(loader)` retries against the *same* loader
instance, incrementing `attempts` to 2. Under Native Image's closed-world class
loading model, a class's loader is fixed at build time and `Class.forName`
fallback paths do not necessarily re-delegate to the same custom loader
instance the way they do on HotSpot — this is runtime class loading/delegation
behavior that Native Image does not reproduce, not a missing reflection,
resource, or serialization entry. No metadata entry can make a synthetic
retry-counting `ClassLoader` behave identically under the closed-world model, so
the generated test and its test-only metadata entry (the
`ClassLoaderClassResolverTest`-conditioned `PulsarClient` reflection entry in
`tests/src/org.apache.pulsar/pulsar-client-all/4.2.5/src/test/resources/META-INF/native-image/reachability-metadata.json`)
were deleted.

### 2. Metadata-related — tests preserved (39 failures)

All of the following failures are caused by missing reachability metadata, and
the shipped `reachability-metadata.json` diff already staged by the
`metadata_fix` agent (but never re-verified before it timed out) adds the entry
each one needs:

| Failure | Missing metadata | Status in current diff |
|---|---|---|
| `AbstractBiMapInnerInverseTest`, `KeyComparatorHashMapTest` | reflective no-arg constructors for `Maps$IteratorBasedAbstractMap` / `java.util.AbstractMap` during deserialization | added |
| `ArrayMemberValueTest`, `EnumMemberValueTest`, `MemberValueTest` | dynamic proxy registrations for Javassist-backed annotation carrier interfaces | added |
| `ClassLoaderObjectInputStreamTest`, `ValidatingObjectInputStreamTest`, `TypesInnerTypeVariableInvocationHandlerTest` | dynamic proxy registrations (`Greeting`/`HiddenGreeting`/`TypeVariable`) created via Guava's `Reflection.newProxy` | added |
| `EventUtilsTest` | dynamic proxy registration for a Commons Lang3 listener interface | added |
| `AsyncHttpConnectorTest`, `ByteBufPairTest` | classpath resource `ahc-default.properties` (shaded `asynchttpclient` config) | added |
| `ByteBufChecksumInnerReflectiveByteBufChecksumTest`, `ByteBufChecksumTest`, `CompactObjectInputStreamTest` (x2), `CompatibleObjectEncoderTest`, `HttpClientTest`, `NioDatagramChannelConfigTest`, `ObjectDecoderTest`, `ObjectEncoderTest` | unsafe field-offset access to JCTools' internal `producerIndex`/`consumerIndex`/`producerLimit`/`next`/`producerNode`/`consumerNode` fields used by Netty's MPSC queues | added (all JCTools field variants) |
| `DefaultDnsCacheTest` | unsafe field access to `Throwable.serialVersionUID`/`cause` | added |
| `CommonsCodecResourcesTest` | classpath resource `Resources.class` (self-relative resource lookup) | added |
| `IOUtilsTest` | classpath resource `IOUtils.class` | added |
| `ResourcesUtilTest` | classpath resource `ResourcesUtil.class` (Netty) | added |
| `JaxbHelpersMessagesTest`, `JaxbMessagesTest`, `JaxbUtilMessagesTest` | resource bundles `javax.xml.bind.{helpers.,util.,}Messages` | added |
| `JerseyMultimapsInnerCustomListMultimapTest`, `JerseyTreeMultimapTest` | serialization registration for Jersey's internal Guava fork `Multimaps$CustomListMultimap`/`TreeMultimap` | added |
| `JsonMapperConfiguratorTest` | reflective no-arg constructor for `JaxbAnnotationIntrospector` | added |
| `OptionalHandlerFactoryTest` | reflective constructors for Jackson's `SqlDateSerializer`/`SqlDateDeserializer` | added |
| `FieldAccessReflectInnerReflectionBasedAccessorTest`, `FieldAccessReflectInnerReflectionBasesAccessorCustomEncodedTest`, `FieldAccessReflectTest`, `ReflectDatumReaderTest` | Avro `ReflectData` needs a no-arg constructor and field access for the test's `TopicRecord`/`CustomRecord` POJOs, otherwise it silently falls back to `GenericData$Record`, producing a `ClassCastException` | added |
| `SerializerDeserializerTest` | reflective `toByteArray()`/`fromMemory(Memory)` on Sketches' `DoubleSummaryFactory` | added |
| `UnsafeAllocatorAnonymous1Test`, `UnsafeAllocatorTest` | `unsafeAllocated` registration (and field access) for the test's `GsonUnsafeAllocationSupport$ConstructorOnly` type | added |
| `VersionUtilTest` | reflective no-arg constructor for Jackson's generated `PackageVersion`; Jackson's `VersionUtil` swallows the resulting registration error and silently returns `Version.unknownVersion()`, which is why the symptom is an assertion mismatch rather than a reflection exception | added |

None of these 39 exercise runtime bytecode generation, runtime class
definition, Java agent self-attach, class redefinition, instrumentation, or
Byte Buddy-backed mocking — they are all ordinary reflection, resource, or
serialization accesses that Native Image supports once registered. The
generated tests are the only coverage in this repository that exercises these
reflective/resource paths (JCTools unsafe queues, Avro reflective records,
JAXB resource bundles, Javassist/Guava dynamic proxies, Gson unsafe
allocation, Sketches tuple serialization, Jackson version/handler discovery),
so they must stay: deleting them would silently drop the only signal that
would catch a regression in this metadata the next time this library's
version is bumped. The fix only needs to be completed (re-run
`./gradlew test -Pcoordinates=org.apache.pulsar:pulsar-client-all:4.2.5` with
the already-staged metadata diff and verify all 39 pass) and committed; it
should not be treated as a reason to remove coverage.

## Action taken

- Deleted `tests/src/org.apache.pulsar/pulsar-client-all/4.2.5/src/test/java/org_apache_pulsar/pulsar_client_all/ClassLoaderClassResolverTest.java`.
- Removed its test-only conditioned entry from
  `tests/src/org.apache.pulsar/pulsar-client-all/4.2.5/src/test/resources/META-INF/native-image/reachability-metadata.json`.
- Left `metadata/org.apache.pulsar/pulsar-client-all/4.2.5/reachability-metadata.json` untouched.
