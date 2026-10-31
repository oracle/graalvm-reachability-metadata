# Test coverage

`Trino_jdbcTest` exercises JDBC query submission, protocol JSON binding,
pagination, typed values, SQL nulls, and result metadata against a loopback HTTP
server. `Jdk9PlatformTest` repeats query execution over certificate-verified TLS.
The bundled `localhost.p12` contains a self-signed, test-only localhost key and
certificate; its password is `changeit`. Neither test requires external services.
`UtilTest` configures a TLS 1.2-only loopback server and an OkHttp connection-spec
fallback list. It verifies a successful request after two TLS 1.3 handshake
failures and checks that the first failure retains the second as a suppressed
exception.

Dedicated tests exercise the public APIs of the bundled Jackson, Guava, and
Joda-Time components. Reflection appears only where it is the API's input or
output: Guava's `Invokable`, `Enums`, and generic type resolution. No test calls
private library code. `SerializationTest` exercises the supported `Serializable`
contract of the bundled Guava collections through object-stream round trips,
checking bidirectional mappings, ordered multimap values, multiset counts, and
independent mutation after deserialization.

Additional Jackson scenarios exercise no-argument creator factories, field and
method injection, field-backed builders, property-based identity binding and
reference output, an `Object` accessor mix-in, interface-typed set multimaps,
and canonical type resolution without a thread context classloader.

Some reported accesses have no suitable path in this suite:

- Joda-Time property serialization hooks, `ISOChronology.Stub`, and
  `ByteString.readObject` require Java object-stream round trips outside the
  collection serialization scenarios covered here. JDBC does not expose that
  behavior.
- Android platform, certificate cleaner, trust-root, and close-guard paths need
  Android runtime classes. Jetty boot ALPN paths require an older JVM with the
  boot-classpath ALPN extension. The tests use the configured modern JVM and
  do not provide shadow runtime classes.
- `FinalizableReferenceQueue.DecoupledLoader` requires classloader isolation;
  `ClassPath.ClassInfo` and `ClassPath.ResourceInfo` require classpath scanning.
  Tests do not introduce custom classloaders or depend on scanning JVM JARs in
  a native executable.
- `Throwables`' legacy stack-access reflection is selected only when old JDK
  shared-secret APIs are available. No JDK compatibility overrides are added.
- `ClassUtil.EnumTypeLocator`'s empty enum-container introspection and the base
  OkHttp `Platform`'s socket-factory introspection inspect private JDK fields.
  The suite does not open JDK modules merely to enter these fallback paths.

`JacksonAnnotationIntrospectorTest` also exercises the public single-enum-name
introspection API, checking both annotation-selected names and the default name.
`VersionUtilTest` checks the documented unknown-version result when Maven
`pom.properties` information is absent from the shaded driver, using the actual
driver classloader without fabricated resources or a pinned version.

The remaining Jackson accesses include object-stream `readResolve` hooks,
unused deprecated reflection wrappers in `ClassUtil`, its missing-class
classloader recovery branch, and `AnnotatedMethod.callOn`, which has no caller
in the bundled Jackson code. The second lookup loop in
`GuavaMultimapDeserializer.findTransformer` repeats the first loop with the
same class, method names, and parameter type; no supported mapper input makes
only the second lookup succeed. These paths are not forced through reflection
or custom classloaders.

Partially reached classes may retain serialization, legacy-runtime, or
classloader-recovery call sites. Tests do not force private hooks through direct
reflection or tolerate Native Image exceptions.
