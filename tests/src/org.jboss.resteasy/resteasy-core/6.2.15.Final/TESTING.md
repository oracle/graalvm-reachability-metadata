# RESTEasy core tests

Tests use RESTEasy's in-memory dispatcher, public injection SPI, converters,
URI APIs, and service-provider discovery. The multipart provider is needed for
`EntityPart.Builder` discovery. Jakarta REST and annotation APIs are explicit
dependencies because RESTEasy declares them as provided.

`src/test/resources/META-INF/jandex.idx` indexes only `ScannerFixture` and its
three nested fixture types. It was generated with Jandex's `Indexer` and
`IndexWriter` from the compiled fixture source. It is an input to resource
scanning, not Native Image reachability configuration. Keeping the index as a
classpath resource lets the same discovery test run without scanning class
files at native runtime.

The following report targets have only Security Manager branches. They cannot
be exercised on the required JDK, where installing a Security Manager is no
longer supported. No tests enable legacy compatibility flags or invoke these
anonymous actions directly:

- `ConstructorInjectorImpl$1`
- `PropertyInjectorImpl$1`
- `PropertyInjectorImpl$2`
- `ResourceMethodRegistry$1`
- `RegisterBuiltin$2`
- `RegisterBuiltin$3`
