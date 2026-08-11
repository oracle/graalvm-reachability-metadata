# Post-generation intervention report

Library: com.oracle.oci.sdk:oci-java-sdk-addons-sasl:3.63.1

Stage: `metadata_fix_failed`

## Summary

No generated tests were removed. Native-image compilation failed before any test executed because the
Jackson Databind metadata from `metadata/com.fasterxml.jackson.core/jackson-databind/2.15.2` registers
classes that are absent from the resolved Jackson Databind `2.17.1` dependency.

The supplied Gradle output fails on
`com.fasterxml.jackson.databind.ser.std.SqlDateSerializer`; the Codex reproduction subsequently fails
on `com.fasterxml.jackson.databind.ext.SqlBlobSerializer`. Both obsolete registrations occur in the
same `2.15.2` metadata file and are activated during image construction.

## Root cause and required metadata work

This is metadata-related, not an unsupported native-image behavior or a test defect. The Jackson
metadata-version selection is applying `2.15.2` registrations to `2.17.1`, where the referenced
serializer classes no longer exist. The missing correction is to restrict, remove, or replace those
obsolete Jackson serializer registrations for the resolved version; adding reachability metadata for
the SASL test cannot make absent Jackson classes available.

Codex treated the failure as a conventional metadata-repair loop and collected agent output. It also
produced test-class `typeReached` conditions that `checkMetadataFiles` rejected. That work could not
resolve the build-time missing-type error, because the broken registrations belong to the transitive
Jackson metadata rather than to the generated OCI SASL support. Metadata files were intentionally not
modified in this intervention.

## Preserved generated support

The generated tests exercise supported OCI SASL behavior: provider registration and client creation,
initial key exchange, configured user-principal login-module initialization, and mechanism lookup.
They do not use runtime bytecode generation, class definition/loading, agent attachment,
instrumentation, substitutions, or Byte Buddy mocking. Keeping them preserves meaningful coverage;
the failure is isolated to incompatible transitive Jackson metadata.
