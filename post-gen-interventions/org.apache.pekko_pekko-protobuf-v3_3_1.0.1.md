# Post-generation intervention report

Library: org.apache.pekko:pekko-protobuf-v3_3:1.0.1
Stage: metadata_fix_failed

## Summary

`nativeTestCompile` fails before the native test executable is produced. GraalVM Native Image aborts during reflection metadata registration with:

```text
Bulk queries can only be set with 'name' which does not allow run-time conditions.
```

This is not a JVM test assertion failure and not an unsupported Pekko runtime feature. The failure is in the generated/collected reachability metadata shape used by the native-image build.

## Root cause

The Codex metadata-fix log shows that Codex progressed past ordinary missing-registration work and narrowed the remaining problem to conditional reflection metadata entries that use bulk field queries. Native Image rejects that combination under `future-defaults-all`: bulk field-query metadata must be encoded as legacy `name`-based `reflect-config.json` entries, but those entries cannot carry run-time conditions.

The log identified test-generated conditional bulk field registrations for these entries:

- `org_apache_pekko.pekko_protobuf_v3_3.MessageSchemaMissingFieldMessage`
- `org_apache_pekko.pekko_protobuf_v3_3.SchemaUtilCoverageMessage$AttributesDefaultEntryHolder`
- `org_apache_pekko.pekko_protobuf_v3_3.UnsafeUtilAndroid32MemoryAccessorCoverageMessage$AttributesDefaultEntryHolder`
- `org_apache_pekko.pekko_protobuf_v3_3.UnsafeUtilAndroid64MemoryAccessorCoverageMessage$AttributesDefaultEntryHolder`
- `sun.misc.Unsafe`

Codex also found and partially addressed a separate native-image classpath issue: `scala3-compiler_3` on `testRuntimeClasspath` pulled JLine artifacts whose embedded native-image metadata contains bulk field queries. Changing the compiler dependency to compile-only removes that runtime noise, but it does not solve the remaining Pekko metadata encoding problem.

## Intervention decision

No generated Pekko support test was removed. The remaining failure is metadata-related: Codex did not complete a valid metadata representation for the conditional bulk-field cases, and this cannot be fixed by deleting an individual generated test without losing coverage for real dynamic-access paths.

No metadata files were modified during this intervention.

## Why the remaining generated support should be preserved

The generated tests exercise meaningful `pekko-protobuf-v3_3` behavior: descriptor construction, extension registries, generated-message reflection paths, schema handling, `ByteBufferWriter`, serialized-form handling, and unsafe/Android-accessor branches. Those tests expose real dynamic accesses that the reachability metadata must eventually support.

Preserving the generated support is valuable because the current native-image failure points to an unresolved metadata encoding limitation, not to invalid library usage. Once the conditional bulk-field metadata is split or represented in a GraalVM-compatible way, the existing tests should continue to verify that the library metadata covers these paths.
