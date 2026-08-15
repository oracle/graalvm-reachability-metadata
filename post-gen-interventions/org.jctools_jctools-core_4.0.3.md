# Post-generation intervention report

Library: org.jctools:jctools-core:4.0.3

Stage: `metadata_fix_failed`

## Summary

The native test run found six tests: `JCToolsCoreTest.testSpscUnboundedArrayQueue()` passed, while five tests failed during JCTools class initialization. All five failures are metadata-related. JCTools calls `Class.getDeclaredField(...)` from `org.jctools.util.UnsafeAccess.fieldOffset(...)` to obtain offsets for fields used with `sun.misc.Unsafe`, but the required fields are not available for reflection in the native image.

No generated tests were removed, and no metadata files were modified.

## Failure root causes

- `NonBlockingHashMapAnonymous2Test.keySetConvertsEntriesToTypedArray()` fails because `org.jctools.maps.NonBlockingHashMap` cannot reflectively find its `_kvs` field.
- `NonBlockingHashMapLongTest.serializationRoundTripPreservesPrimitiveLongEntries()` fails because `org.jctools.maps.NonBlockingHashMapLong` cannot reflectively find its `_chm` field.
- `NonBlockingHashMapTest.serializationRoundTripPreservesEntries()` reports `NoClassDefFoundError` because the earlier missing `_kvs` registration caused `NonBlockingHashMap` class initialization to fail.
- `NonBlockingIdentityHashMapTest.serializationRoundTripPreservesIdentityEntries()` fails because `org.jctools.maps.NonBlockingIdentityHashMap` cannot reflectively find its `_kvs` field.
- `UnsafeAccessTest.linkedQueueTransfersElementsInOrder()` fails because `org.jctools.queues.BaseLinkedQueueProducerNodeRef` cannot reflectively find its `producerNode` field.

The existing metadata only registers `consumerIndex` and `producerIndex`, with conditions tied to the classes that declare those fields. Codex determined that these conditions become active too late: the field lookup occurs through `org.jctools.util.UnsafeAccess` while the target class is still initializing. The metadata repair therefore needs earlier conditions based on `org.jctools.util.UnsafeAccess` and registrations for the missing `_kvs`, `_chm`, and `producerNode` fields. Codex's bytecode inspection also identified `_val_1` as another field needed by the exercised map path and likely to surface after the first missing registrations are fixed.

## Why Codex could not complete the repair

Codex verified the requested GraalVM distribution and diagnosed the missing registrations from the preserved native test output. However, enterprise-managed policy replaced the requested unrestricted execution profile with a restricted profile requiring approval. Codex exec mode could not request that approval, so both the Gradle reproduction command and the metadata patch were rejected. It could not apply or verify the metadata repair.

## Why the generated support should be preserved

These tests exercise distinct public JCTools behavior: unbounded queues, linked queues, non-blocking maps, identity-key maps, primitive-long maps, typed key-array conversion, and serialization round trips. Their failures expose concrete missing reflection metadata rather than unsupported runtime bytecode generation, instrumentation, class redefinition, agent attachment, substitutions, or Byte Buddy mocking. Removing them would hide real reachability requirements and discard useful coverage. The passing `SpscUnboundedArrayQueue` test and all five failing tests should therefore remain so the metadata can be completed and verified in a later repair run with workspace write and Gradle execution permissions.
