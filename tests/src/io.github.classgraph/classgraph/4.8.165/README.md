# ClassGraph scan fixtures

`src/test/resources/scan-fixtures.data` is a JAR containing only this suite's
`ScanFixtures` classes and `catalog/payload.txt`. Tests copy it to a temporary
JAR and scan its bytes, so scanning does not depend on a machine's classpath.
The fixture types also compile normally into the test application. Class-loading
tests delegate to that application's existing class loader; they never define
new classes from the fixture JAR.

To regenerate the fixture archive from the repository root using `JAVA_HOME`:

```sh
project=tests/src/io.github.classgraph/classgraph/4.8.165
"$JAVA_HOME/bin/javac" -d "$project/build/scan-fixtures" \
  "$project/src/test/java/io_github_classgraph/classgraph/ScanFixtures.java"
"$JAVA_HOME/bin/jar" --create --no-compress \
  --file "$project/src/test/resources/scan-fixtures.data" \
  -C "$project/build/scan-fixtures" . \
  -C "$project/src/test/resources" catalog/payload.txt
```

The optional JVM-Driver and Narcissus reflection backends are not exercised:
the test dependencies do not include those backends, and requesting them without
the actual implementations would only test fallback to standard reflection.
Their legacy JVM-specific class-finding branches cannot be selected portably on
the test JVM. All other tests use only `io.github.classgraph` public APIs.

## Remaining dynamic-access limits

`StandardReflectionDriverTest` also reads enum constant objects through
`ClassInfo.getEnumConstantObjects()`, exercising the standard driver's static
field reads without test-side reflection.

The remaining reported sites are not forced through internal APIs:

- `JVMDriverReflectionDriver`, its four anonymous class finders, and
  `NarcissusReflectionDriver` require their actual optional backend libraries.
  The first three JVM-Driver finders additionally depend on JVM-specific private
  `Class` methods; the fourth is selected only if those methods are unavailable.
- `CallStackReader`'s security-manager route is selected only below JDK 9.
  Its stack-trace class-loading fallback is used only when the normal stack
  walker fails. `SimpleThreadFactory`'s remaining calls require a non-null
  security manager. Tests do not alter JVM version detection or install one.
- `FileUtils`'s remaining cleaner calls belong to its pre-JDK-9 branch. The
  existing memory-mapped archive test exercises the current-JDK cleaner route.
- `StandardReflectionDriver`'s remaining accessibility call is the fallback
  after `trySetAccessible`; `ReflectionDriver`'s is the legacy fallback after
  `canAccess`. Normal successful access does not select either fallback.
  Driver field setters have no consumer-facing setter API, and constructor
  enumeration is used to initialize the optional JVM-Driver backend.
- `ClassGraphClassLoader`'s additional class-finding routes require other
  delegation arrangements; tests load only classes already in the application.
  Its resource methods dereference delegation sets that can be null and, in
  the environment route, a null loader entry. Tests do not assert those null
  dereferences as supported behavior or introduce runtime-defined classes.
- `ScanResultObject` and `ArrayTypeSignature`'s remaining class-loading calls
  are fallbacks for objects with no associated scan result. Public scanning
  and scan-result restoration attach that result to the returned objects.
- `FieldTypeInfo` and `JSONUtils`'s remaining numeric field accesses require
  primitive numeric fields in the serialized object graph. Scan-result state
  uses boxed annotation numbers, not those primitive fields. Their other
  remaining calls concern explicit null/class-reference fields and an
  accessibility fallback, respectively.
- `JSONDeserializer`'s remaining class-reference and multidimensional-array
  branches, and `TypeResolutions`' generic-array branches, require object
  shapes not exposed by scan-result serialization. Tests do not invoke the
  `nonapi` object serializer/deserializer directly with arbitrary test beans.
- `NestedJarHandler.runFinalizationMethod()` looks up the unqualified class
  name `System`, so ordinary scans cannot reach the method invocation. Tests
  do not supply a shadow `System` class or inject the cached method.
