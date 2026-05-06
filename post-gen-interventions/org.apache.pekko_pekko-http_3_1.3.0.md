# Post-generation intervention report

Library: org.apache.pekko:pekko-http_3:1.3.0
Stage: metadata_fix_failed

## Summary

The native test executable builds, but all six generated tests fail immediately while creating a Pekko `ActorSystem` in the shared `withSystem` helper. The reported failure is:

```text
com.typesafe.config.ConfigException$Missing: No configuration setting found for key 'pekko.loggers'
```

The requested Codex metadata-fix log was not present at `logs/org.apache.pekko:pekko-http_3:1.3.0/metadata-fix/codex.log`, so this assessment is based on the Gradle/native-test output and the generated workspace artifacts.

## Root cause by failing test

All failures have the same root cause and occur before the individual HTTP feature under test is exercised:

- `cookieDirectivesReadRequestCookiesAndSetResponseCookies()`
- `requestBuildingMarshallingAndUnmarshallingRoundTripFormsAndText()`
- `gzipCoderCompressesEntitiesAndRestoresOriginalBytes()`
- `multipartFormDataPreservesPartNamesFilenamesAndContent()`
- `routeEvaluatesPathQueryHeaderAndEntityDirectives()`
- `serverSentEventsMarshalToEventStreamAndUnmarshalBack()`

The missing `pekko.loggers` key is a Pekko default configuration value normally provided by Pekko `reference.conf` resources. The JVM test run succeeds, while the native run cannot see the required default configuration during `ActorSystem` initialization. This points to incomplete reachability metadata for Pekko/Typesafe Config resource handling rather than a bug in any one generated test method.

Codex appears to have only partially addressed the metadata needs. The remaining missing support is the native-image configuration required for Pekko's default `reference.conf` configuration to be available and merged at runtime, including the settings used by `ActorSystem$Settings.amendSlf4jConfig` such as `pekko.loggers`.

## Intervention decision

No generated tests were removed. The failure is metadata-related: the tests pass on the JVM and fail in native image because required Pekko configuration resources/defaults are unavailable at runtime.

## Why the remaining generated support should be preserved

The generated tests cover meaningful Pekko HTTP functionality: routing directives, request building, form marshalling/unmarshalling, multipart entities, gzip coders, cookies, and server-sent events. These tests expose real native-image metadata gaps in Pekko actor/config initialization that affect all exercised APIs. Preserving the generated support keeps broad coverage for `pekko-http_3` once the remaining metadata for Pekko default configuration resources is completed.
