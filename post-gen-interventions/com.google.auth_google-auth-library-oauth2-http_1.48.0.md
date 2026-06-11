# Post-generation intervention report

Library: com.google.auth:google-auth-library-oauth2-http:1.48.0
Stage: metadata_fix_failed

## Summary

The native test run built the image successfully, but `:nativeTest` failed in one generated test:

- `AppEngineCredentialsTest.serializedApplicationDefaultCredentialsReinitializesAppIdentityService()` failed during `ObjectInputStream.readObject()` with `java.lang.ClassNotFoundException: com.google.auth.oauth2.AppEngineCredentials`.

All other generated tests listed in the failure output passed.

## Root cause

This failure is metadata-related. The failing test serializes and deserializes `GoogleCredentials.getApplicationDefault()` when the library resolves to the concrete `com.google.auth.oauth2.AppEngineCredentials` implementation. In the native image, Java deserialization attempted to resolve `com.google.auth.oauth2.AppEngineCredentials`, but that class was not available through the serialization metadata used by the failing image.

The Codex metadata-fix log confirms that Codex investigated the same serialization graph and identified the App Engine stream descriptors, including:

- `com.google.auth.oauth2.AppEngineCredentials`
- `com.google.auth.oauth2.GoogleCredentials`
- `com.google.auth.oauth2.OAuth2Credentials`
- `com.google.auth.Credentials`
- `java.time.Ser`
- `byte[]`
- `java.lang.Object[]`
- `com.google.common.collect.ImmutableList$SerializedForm`

Codex then began editing the metadata, but the log ends immediately after the file-change operation and does not include a successful verification run. The remaining Gradle failure shows the fix was incomplete or unverified: at minimum, effective serialization registration for `com.google.auth.oauth2.AppEngineCredentials` was still missing from the native image that ran the test.

## Intervention decision

No generated test was removed. The failure is not caused by an unsupported platform feature or by a test-only bug; it is exposing missing reachability metadata for the library's App Engine credentials serialization path.

## Why the remaining generated support should be preserved

The generated support exercises meaningful library behavior, and six of seven native tests passed. The preserved tests cover application default credential detection, App Engine token refresh/signing, client-id resource loading, default credential App Engine signal checks, ID token parsing/serialization, and user-credential serialization with default and custom transport factories. The single remaining failure is a narrow metadata gap in the App Engine credentials deserialization path, so keeping the generated tests preserves useful coverage and provides a clear reproducer for the missing metadata.
