# Post-generation intervention report

Library: com.oracle.oci.sdk:oci-java-sdk-addons-sasl:3.63.1
Stage: `metadata_fix_failed`

## Summary

`nativeTestCompile` fails during Native Image runtime-metadata construction, before tests run. The supplied failure names `DateDeserializers$SqlDateDeserializer`; the Codex retry instead fails on `DateDeserializers$TimestampDeserializer`. Both entries come from the selected `jackson-databind` reachability metadata (`2.15.2`), while the resolved dependency is `jackson-databind:2.17.1`. The classes exist in the resolved JAR, but GraalVM 25.0.4 cannot add them to its analysis universe when building their runtime metadata.

This is metadata-related, not unsupported behavior in a generated test. No generated tests or test-only support files were removed, and no metadata files were modified.

## Remaining metadata work

The Jackson metadata selected for this dependency graph needs version-compatible conditional reflection handling for `DateDeserializers$SqlDateDeserializer` and `DateDeserializers$TimestampDeserializer` (or an equivalent correction that prevents unreachable analysis types from being emitted). Codex verified the exact required GraalVM and `checkMetadataFiles`, but could not resolve this dependency-metadata/Native Image analysis failure; it explicitly stopped after confirming the affected classes are present in `jackson-databind:2.17.1`.

## Why preserve the generated support

The generated suite exercises OCI SASL mechanism discovery, JAAS configuration loading, cached authentication callbacks, provider registration, and the signed SASL challenge exchange. These are library-specific, ordinary API paths and do not rely on unsupported runtime bytecode generation, class redefinition/loading, self-attach, or inline mocking. The failure occurs before any test executes and is attributable to selected Jackson metadata, so removing the OCI SASL tests would hide a metadata compatibility defect and discard valid coverage.
