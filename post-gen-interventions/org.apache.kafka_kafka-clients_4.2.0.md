# Post-generation intervention report

Library: org.apache.kafka:kafka-clients:4.2.0
Stage: metadata_fix_failed

## Summary

The native test run failed because the generated test set mixed real reachability-metadata gaps with tests that rely on JVM-only behavior or an incomplete generated runtime setup. I removed only the generated support that caused non-metadata failures and kept the tests that still represent metadata coverage.

## Root cause by failure

- `OrgApacheKafkaClientsConsumerInternalsFetcherTestAnonymous4Anonymous1Test.testConcurrentFetchSessionVerification()` was not a Kafka metadata failure. It exercised Kafka's `FetcherTest`, which initializes Mockito/Byte Buddy. The failure came from Mockito's inline mock maker trying to use Java agent attachment inside a native image (`Could not initialize inline Byte Buddy mock maker` / `net.bytebuddy.agent.Installer.getInstrumentation()`), which is a native-image limitation rather than missing Kafka reachability metadata. The Codex log also showed that switching to the subclass mock maker only moved the failure to loading `org.mockito.internal.creation.bytebuddy.ByteBuddyMockMaker`. I removed this generated test and the Mockito-only generated test resource/configuration.

- `OrgApacheKafkaCommonConfigAbstractConfigTestAnonymous1RestrictedClassLoaderTest.resolvesVisibleClassesThroughRestrictedContextClassLoader()` is metadata-related and was preserved. The failure is an inactive reflection registration for Kafka test reporter constructors, first reported for `org.apache.kafka.common.metrics.FakeMetricsReporter()` and later in the Codex log for `org.apache.kafka.common.config.AbstractConfigTest$ConfiguredFakeMetricsReporter()`. The remaining gap is that the constructor metadata is conditioned too narrowly, so the condition is not satisfied before `org.apache.kafka.common.utils.Utils.newInstance()` reflectively invokes the constructor.

- `OrgApacheKafkaShadedComGoogleProtobufUnsafeUtilInnerAndroid32MemoryAccessorTest.generatedMessageMapParsingReadsDefaultEntryHolderStaticField()` is metadata-related and was preserved. Shaded protobuf's `DescriptorMessageInfoFactory` reflectively queries `Android32ProtobufMapParsing$MapBackedMessage.getDefaultInstance()`. The original failure shows this method was not available to reflection in the native image. Codex attempted to add metadata for this generated helper and its default-entry holder, but the metadata-fix log did not end with a successful final verification.

- `OrgApacheKafkaShadedComGoogleProtobufUnsafeUtilInnerAndroid32MemoryAccessorTest.generatedMessageMapParsingUsesAndroid32MemoryAccessorWhenOnly32BitAndroidMemoryApiIsAvailable()` was not metadata-related. It depended on a generated `android32.runtime.classpath` test property and child-first loading of Android/Robolectric classes. The failure (`Expecting not blank but was: null`) is a generated test setup bug / unsupported platform simulation, not missing Kafka metadata. I removed this test method and the Android32-only test dependency/configuration.

## Why the remaining generated support should be preserved

The remaining generated tests still exercise meaningful Kafka native-image behavior: reflective construction through `AbstractConfig`/`Utils.newInstance()` and shaded protobuf schema/map parsing. These are realistic reachability paths for `kafka-clients` and should continue to drive metadata completion. Removing them would hide real metadata gaps; only the Mockito agent-attachment test and the Android32 classloader simulation were pruned because they do not represent fixable Kafka reachability metadata issues.

No metadata file was modified by this intervention.
