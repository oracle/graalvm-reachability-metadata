# Post-generation intervention report

Library: org.apache.pekko:pekko-http-core_2.13:1.3.0
Stage: metadata_fix_failed

## Summary

The reported failure is metadata-related, so the generated `Http2AlpnSupportTest` should not be removed. The visible Gradle failure was an assertion in `Http2AlpnSupportTest.http2ClientConnectionChecksJdkAlpnCompatibility()`: the test waited for the custom `SSLEngine` factory to run, but `engineRef` stayed `null` and the assertion failed.

The Codex metadata-fix log shows that this was not a test-only bug. The top-level assertion hid native-image metadata gaps in Pekko's bootstrap and HTTP/2 ALPN detection paths.

## Root cause

At the failure point, the generated test was exercising real dynamic access used by `pekko-http-core_2.13`:

- `org.apache.pekko.util.Unsafe` reflectively looks up `sun.misc.Unsafe.theUnsafe`. Without metadata for that field, clean native runs can fail during actor-system bootstrap with Pekko reporting that it cannot find an instance of `sun.misc.Unsafe`.
- `org.apache.pekko.http.impl.engine.http2.Http2AlpnSupport$` checks JDK ALPN support by reflectively enumerating methods on `javax.net.ssl.SSLEngine` and `sun.security.ssl.ALPNExtension`. Without query metadata for these classes, Pekko can incorrectly conclude that JDK ALPN support is unavailable, which leads to the observed failed assertion around HTTP/2 ALPN setup.

Codex could not identify the issue from the first Gradle output because the failure surfaced as a normal assertion failure rather than a `Missing*RegistrationError`. The log shows it required trace-enabled native runs and bytecode inspection of `Http2AlpnSupport$` to identify the missing reflective query metadata.

## Intervention decision

No generated test was removed. The failure is tied to missing native-image reachability metadata, not to an unsupported platform feature or an invalid generated test.

## Why the generated support should be preserved

The generated support covers meaningful Pekko HTTP core behavior: actor-system startup, TLS client connection setup, and HTTP/2 ALPN capability detection. These are legitimate runtime paths for `org.apache.pekko:pekko-http-core_2.13:1.3.0`, and the failure exposed concrete metadata requirements rather than dead or scaffold-only test code. Preserving the test keeps coverage for the dynamic access that the metadata is meant to support.
