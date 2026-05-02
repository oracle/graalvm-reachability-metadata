# Post-generation intervention report

Library: io.javaslang:javaslang:2.0.6
Stage: metadata_fix_failed

## Summary

The native test run still fails after the Codex metadata-fix attempt. The failures are metadata-related: all remaining failing tests throw `org.graalvm.nativeimage.MissingReflectionRegistrationError` while Java serialization is deserializing Javaslang collection serialization proxies.

No generated test was removed, because the failures are not test bugs, unsupported platform features, or unrelated native-image limitations. They are unresolved reachability metadata gaps.

## Root cause by failing test

- `HashSetInnerSerializationProxyTest.roundTripsNonEmptyHashSetThroughSerializationProxy()` fails during `ObjectInputStream.readObject()` with missing reflective access to `java.lang.Object.<init>()` while deserializing the `javaslang.collection.HashSet` serialization-proxy object graph.
- `LinkedHashSetInnerSerializationProxyTest.roundTripsNonEmptyLinkedHashSetThroughSerializationProxy()` fails with the same missing `java.lang.Object.<init>()` reflection registration while deserializing the `javaslang.collection.LinkedHashSet` serialization-proxy object graph.
- `TreeInnerNodeInnerSerializationProxyTest.roundTripsNodeWithChildrenThroughSerializationProxy()` fails with the same missing `java.lang.Object.<init>()` reflection registration from `ObjectStreamClass.newInstance`, reached through `javaslang.collection.Tree$Node$SerializationProxy.readObject()`.

## Codex metadata-fix outcome

Codex tried to repair metadata and reran `./gradlew test -Pcoordinates=io.javaslang:javaslang:2.0.6 --stacktrace` several times. It also tried `./gradlew generateMetadata -Pcoordinates=io.javaslang:javaslang:2.0.6`, `nativeTraceImage`, and `runNativeTraceImage`.

The fix attempt was only partial. `LazyTest`, `ValueTest`, and `StreamModuleInnerSerializationProxyTest` pass, but Codex could not find a metadata form that satisfies the remaining Java serialization allocation path. The unresolved metadata is still around deserialization of the HashSet, LinkedHashSet, and Tree node serialization proxy graphs: Native Image continues to report missing reflective invocation of `java.lang.Object.<init>()` from `ObjectStreamClass.newInstance`, even after attempts involving `serializable`, `unsafeAllocated`, and explicit `java.lang.Object` constructor registrations.

## Why the remaining generated support should be preserved

The generated tests exercise real dynamic features of `io.javaslang:javaslang:2.0.6`: `Lazy.val` proxy creation, typed array creation through `Value.toJavaArray`, and Java serialization proxy round-trips for persistent collections. The passing tests already validate useful reachability support, and the remaining failing serialization tests identify legitimate metadata coverage gaps rather than invalid generated behavior. Preserving the generated support keeps those successfully covered features and retains high-signal reproducers for the unresolved metadata issue.
