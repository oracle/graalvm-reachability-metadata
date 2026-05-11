# Post-generation intervention report

Library: org.apache.groovy:groovy-json:4.0.22
Stage: metadata_fix_failed

## Summary

The generated `groovy-json` native test failed during `:nativeTest`, causing the top-level `:tckTest` task to fail. The Gradle excerpt shows all `10` JUnit methods starting but failing before meaningful test execution with:

`java.lang.NoClassDefFoundError: Could not initialize class groovy.lang.GroovySystem`

This is metadata-related. No generated test was removed.

## Root cause by failing test

All reported failures have the same root cause: `groovy.lang.GroovySystem` fails during native-image runtime initialization, so subsequent test instances cannot be constructed and report `NoClassDefFoundError`.

Affected tests include:

- `parsesStrictJsonWithEachSlurperParserType()`
- `laxSlurperParsesRelaxedJsonSyntax()`
- `parsesJsonFromReadersStreamsArraysFilesPathsAndUrls()`
- `serializesCommonJsonOutputTypesAndRejectsInvalidJsonNumbers()`
- `customJsonGeneratorHonorsConvertersExclusionsDatesAndUnicode()`
- `buildsJsonDocumentsWithJsonBuilder()`
- `streamsJsonDocumentsWithStreamingJsonBuilder()`
- `prettyPrintsLexesAndUnescapesJson()`
- `indexOverlaySlurperConvertsIsoDateStringsWhenDateCheckingIsEnabled()`
- `classicSlurperParsesObjectsArraysAndNulls()`

The Codex metadata-fix log and saved Gradle outputs show the underlying initializer failure was not a test assertion issue. It came from missing native-image metadata while Groovy bootstrapped its runtime metadata, including missing reflection access for classes loaded from Groovy DGM information such as `java.util.BitSet` and `groovy.util.BufferedIterator`, reached through `org.codehaus.groovy.reflection.GeneratedMetaMethod$DgmMethodRecord`. Codex also reached later unverified callable metadata around Groovy JSON closure classes such as `groovy.json.JsonTokenType$1`.

## Why Codex could not resolve it

Codex started the correct metadata-fix loop and added Groovy bootstrap/resource metadata, but it did not complete a successful verification. The log ends with a verification command still in progress after another metadata/test adjustment, so the last known state is only partially fixed. The remaining issue is still missing or incomplete Groovy runtime reflection metadata, not evidence that the generated JSON API tests are invalid.

## Why the generated support should be preserved

The generated tests exercise real `groovy-json` functionality: `JsonSlurper`, `JsonOutput`, `JsonGenerator`, `JsonBuilder`, `StreamingJsonBuilder`, lexer/token handling, relaxed parsing, date parsing, and classic parsing. These are relevant native-image reachability paths for the requested library. Removing the tests would hide a real metadata gap in Groovy runtime initialization rather than removing an unsupported or unrelated platform feature.

Because the failures are metadata-related, the generated support should remain in place for a follow-up metadata fix. Metadata files were not modified during this intervention.
