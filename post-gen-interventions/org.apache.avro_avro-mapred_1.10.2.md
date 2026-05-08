# Post-generation intervention report

Library: org.apache.avro:avro-mapred:1.10.2

Stage: metadata_fix_failed

## Summary

The native-image test failures are metadata-related. I did not remove any generated tests or test-only support files, and I did not modify metadata files.

The Gradle failure excerpt shows four native runtime failures in `Avro_mapredTest`:

- `avroJobHelpersConfigureLegacyMapReduceJobs()` fails because Hadoop `Configuration` reloads `org.apache.avro.reflect.ReflectData` by class name and the native image cannot find that class.
- `mapreduceKeyValueRecordReaderReadsAvroKeyValueContainerFiles()` fails during Hadoop `FileSystem`/HTrace initialization because shaded commons-logging cannot load `org.apache.htrace.shaded.commons.logging.impl.LogFactoryImpl`.
- `sortedKeyValueFileWritesIndexAndSupportsLookupAndIteration()` fails with `Could not initialize class org.apache.htrace.core.HTraceConfiguration`, which is a follow-on failure from the same HTrace shaded commons-logging initialization path.
- `avroSequenceFileRoundTripsAvroAndWritableRecords()` also fails through Hadoop `FileSystem`/HTrace initialization in the excerpt. The Codex log later narrows the remaining failure in this test to Hadoop serializer discovery: `core-default.xml` is not visible in the native image, so `io.serializations` is empty and Hadoop cannot find `WritableSerialization` for `Text`.

## Root cause

These failures are not caused by invalid generated tests or unsupported platform behavior. They come from incomplete reachability metadata for runtime class/resource lookup used by Avro MapReduce and its Hadoop/HTrace dependencies.

Metadata still needs to cover:

- `org.apache.avro.reflect.ReflectData` class-name lookup and reflective construction via the `ReflectData(ClassLoader)` constructor used by `AvroJob`.
- HTrace shaded commons-logging lookup, especially `org.apache.htrace.shaded.commons.logging.impl.LogFactoryImpl` and related logging implementation classes/resources, for paths reached through Hadoop `FileSystem`/`FsTracer` rather than only through `AvroJob`.
- Hadoop default configuration resources, especially `core-default.xml`, so native runtime `Configuration` can populate `io.serializations` and load `org.apache.hadoop.io.serializer.WritableSerialization`/Avro serialization classes.

Codex partially diagnosed and patched several of these areas, but it did not complete a passing verification loop. The log shows later diagnostic output where `core-default.resource=null`, `core-default.stream=false`, and `io.serializations.get=null` in the native executable, confirming that the remaining blocker is still missing or incorrectly conditioned resource metadata.

## Why generated support should be preserved

The generated tests exercise real `avro-mapred` behavior: Avro wrapper/pair APIs, Avro job configuration helpers, Hadoop Avro key/value readers and writers, sorted key/value files, sequence files, and character-sequence comparators. The JVM test run succeeds, and several native tests already pass. The failing native tests expose legitimate reachability gaps in class-name loading, shaded logging initialization, and Hadoop default resource inclusion. Removing those tests would hide missing metadata rather than remove an invalid or unsupported scenario.
