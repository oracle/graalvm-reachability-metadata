# Test coverage

`Trino_jdbcTest` exercises JDBC query submission, protocol JSON binding,
pagination, typed values, SQL nulls, and result metadata against a loopback HTTP
server. `Jdk9PlatformTest` repeats query execution over certificate-verified TLS.
The bundled `localhost.p12` contains a self-signed, test-only localhost key and
certificate; its password is `changeit`. Neither test requires external services.

Dedicated tests exercise the public APIs of the bundled Jackson, Guava, and
Joda-Time components. Reflection appears only where it is the API's input or
output: Guava's `Invokable`, `Enums`, and generic type resolution. No test calls
private library code or uses Java object serialization to manufacture coverage.

Some reported accesses have no suitable path in this suite:

- Guava collection serialization hooks, `Serialization` and its `FieldSetter`,
  Joda-Time property serialization hooks, `ISOChronology.Stub`, and
  `ByteString.readObject` require direct Java object-stream round trips. JDBC
  does not expose that behavior.
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
- OkHttp `Util.addSuppressedIfPossible` requires multiple connection attempts
  failing inside the connection loop. The loopback tests cover successful
  requests rather than depending on machine-specific route failure ordering.

Partially reached classes may retain serialization, legacy-runtime, or
classloader-recovery call sites. Tests do not force those paths through direct
reflection, object streams, or Native Image exceptions.
