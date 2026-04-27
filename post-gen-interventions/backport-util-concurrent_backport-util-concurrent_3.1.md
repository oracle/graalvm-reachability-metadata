# Post-generation intervention report

Library: backport-util-concurrent:backport-util-concurrent:3.1
Stage: `metadata_fix_failed`

## Summary

The post-generation failure is **metadata-related**, not a bad test or unsupported feature.

`nativeTest` failed in 6 generated serialization tests because Native Image is still missing reflection metadata for the no-arg constructor of `java.lang.Object` during Java deserialization.

No generated tests were removed.

## Failing tests and root cause

All 6 failures have the same root cause:

- `ConcurrentHashMapTest.serializesAndDeserializesEntries()`
- `ConcurrentLinkedQueueTest.serializesAndDeserializesElementsInFifoOrder()`
- `ConcurrentSkipListMapTest.serializesAndDeserializesOrderedEntries()`
- `LinkedBlockingDequeTest.serializesAndDeserializesElementsInDequeOrder()`
- `LinkedBlockingQueueTest.serializesAndDeserializesElementsInQueueOrder()`
- `PriorityQueueTest.serializesAndDeserializesHeapContents()`

Gradle reports the same missing entry for each failure:

```json
{
  "type": "java.lang.Object",
  "methods": [
    {
      "name": "<init>",
      "parameterTypes": []
    }
  ]
}
```

The stack traces show this happens while `ObjectInputStream` is reconstructing serialized backport collection/map instances through library `readObject(...)` code paths such as:

- `edu.emory.mathcs.backport.java.util.concurrent.ConcurrentLinkedQueue.readObject`
- `edu.emory.mathcs.backport.java.util.concurrent.ConcurrentSkipListMap.readObject`
- `edu.emory.mathcs.backport.java.util.concurrent.LinkedBlockingQueue.readObject`
- `edu.emory.mathcs.backport.java.util.PriorityQueue.readObject`

This is a real reachability-metadata gap, not a platform bug in the tests.

## What the Codex metadata-fix log shows

The Codex log shows the metadata-fix workflow recognized this as a serialization/metadata problem:

- the generated metadata file started as an empty stub: `{}`
- Codex reproduced the failure and identified missing native-image registrations in deserialization paths
- it then attempted to repair `reachability-metadata.json` with serialization-related reflection entries

So the failure was not caused by meaningless generated tests. The metadata-fix stage simply did not converge to a complete working metadata set before the workflow stopped.

## What metadata is still missing

Based on the Gradle failure output, the still-missing metadata is at least the reflective no-arg constructor registration for `java.lang.Object`, needed during deserialization of the affected backport container types.

Because all remaining failures are `MissingReflectionRegistrationError` for `java.lang.Object::<init>`, the correct intervention here is **not** to delete the tests. The tests are exercising valid library behavior and are exposing an unresolved Native Image metadata requirement.

## Why the generated support should be preserved

The remaining generated support should stay in place because:

- 38 of 44 generated tests already pass
- the failing tests cover legitimate library functionality: serialization of core backport concurrent collections/maps
- these tests provide useful regression coverage for the exact metadata gap that still needs to be fixed
- removing them would hide a real reachability issue instead of documenting it

## Intervention decision

- **Kept all generated tests**
- **Did not modify metadata files**
- Classified the failure as **metadata-related**
