# Post-generation intervention report

Library: org.scala-lang:scala-library:2.11.8
Stage: metadata_fix_failed

## Summary

The native test run failed because generated Scala runtime tests exercise reflection, method-handle, and Java serialization paths that still need GraalVM reachability metadata. The Codex metadata-fix log shows that several original failures were addressed during the metadata-fix attempt, but the workflow stopped with remaining native-image runtime failures in lambda, `Stream`, and `TrieMap` serialization. I did not remove generated tests because the failures are metadata-related rather than test bugs or unsupported platform features.

## Failure root causes

- `AnyAccumulatorInnerSerializationProxyTest.serializesAndRestoresElementsThroughSerializationProxy()` — metadata-related. Initial failure was a missing reflective field registration for `java.lang.String.serialVersionUID` used by Java serialization.
- `DefaultSerializationProxyTest.serializesAndRestoresKnownSizeElementsThroughDefaultSerializationProxy()` — metadata-related. Initial failure was the same missing `java.lang.String.serialVersionUID` reflective field access during serialization.
- `LazyListInnerSerializationProxyTest.serializesAndRestoresEvaluatedElementsThroughSerializationProxy()` — metadata-related. Initial failure was the same missing `java.lang.String.serialVersionUID` reflective field access during serialization.
- `PropertiesTraitTest.checksConsoleTerminalStatusWhenConsoleIsAvailable()` — metadata-related. Initial failure was missing reflective method access for `scala.util.Properties$.consoleIsTerminal()`.
- `ScalaRuntimeStaticsInnerVMTest.locatesUnsafeInstanceUsedByReleaseFenceFallback()` — metadata-related. `scala.runtime.Statics$VM.findUnsafe` could not find the `Unsafe` singleton because the relevant `sun.misc.Unsafe` fields were not reflectively available in the native image.
- `StreamInnerSerializationProxyTest.serializesAndRestoresForcedElementsThroughSerializationProxy()` — metadata-related. The original failure was `ClassNotFoundException` for `scala.collection.immutable.Stream$Cons`; after Codex added partial serialization metadata, the remaining failure is `MissingReflectionRegistrationError` for `java.lang.Object.<init>()` while `ObjectInputStream` reconstructs the serialized stream graph.
- `SymbolLiteralTest.createsInternedSymbolFromLiteral()` — metadata-related. The symbol literal bootstrap failed to resolve `scala.Symbol.apply(String)`, indicating the method-handle target needed explicit metadata. Codex appears to have resolved this failure.
- `TrieMapTest.serializesAndRestoresEntriesThroughObjectStreams()` — metadata-related. The remaining failure is `MissingReflectionRegistrationError` for `java.lang.Object.<init>()` during `ObjectInputStream` deserialization of the `TrieMap` object graph.
- `TrieMapTest.deserializedTrieMapKeepsConcurrentMapMutationSemantics()` — metadata-related. Same remaining `java.lang.Object.<init>()` deserialization registration gap as the other `TrieMap` serialization test.
- `LambdaDeserializerTest.deserializesSerializableSamLambda()` — metadata-related. The remaining failure is `ClassNotFoundException` for the generated serializable lambda class `org_scala_lang.scala_library.LambdaDeserializerFixtures$$$Lambda/...`, so the lambda serialization metadata is still incomplete or not being applied in the form Codex attempted.

## Remaining missing metadata

The current native test result still reports:

- `java.lang.Object.<init>()` reflective constructor access during Java deserialization of `scala.collection.immutable.Stream` and `scala.collection.concurrent.TrieMap` object graphs.
- Serializable lambda support for the lambda declared by `org_scala_lang.scala_library.LambdaDeserializerFixtures$` implementing `org_scala_lang.scala_library.LambdaDeserializerTextOperation`.

Codex could not finish the fix because the Scala serialization cases require more than the obvious first missing registration: after adding partial metadata, the failures shifted from missing Scala serialized classes to constructor access and lambda class resolution. The log also notes the test project resolves Scala runtime classes from `scala-library:2.13.16` while the requested coordinate is `2.11.8`, which made choosing the correct concrete serialized classes and the correct modern reachability-metadata form for lambda serialization ambiguous.

## Preservation decision

The generated support should be preserved because the tests exercise real dynamic-access behavior in Scala runtime APIs: serialization proxies, `TrieMap` custom serialization, symbol-literal bootstrap method handles, reflective Scala property access, and runtime lookup of `Unsafe`. These are valid reachability-metadata coverage areas. The remaining failures point to incomplete metadata rather than invalid tests, so removing the tests would discard useful coverage and hide metadata gaps that still need to be fixed.
