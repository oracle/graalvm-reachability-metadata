# Coverage scope

Tests exercise the shaded Commons and Velocity APIs shipped in this artifact,
including collection serialization, template resources, bean access, and
introspection. The source archive contains only the uberjar wrapper; the bundled
classes were inspected to find these public API paths.

The legacy `io.sundr.deps.org.apache.commons.lang.enum.Enum` package cannot be
named in Java source because `enum` is a reserved keyword. It has no concrete
implementation in the artifact that a Java consumer can instantiate. It remains
untested rather than using reflection, bytecode generation, or an older Java
source level. The supported `lang.enums.Enum` API has its own test.

The deprecated `commons.collections.ReferenceMap` does not implement
`Serializable`, so its private stream callbacks have no public serialization
route. Its test covers normal map operations; serialization is covered through
`commons.collections.map.ReferenceMap` in `AbstractReferenceMapTest`.

The `ExceptionUtils.isNestedThrowable` method/field fallback follows an early
return when `Throwable.getCause` exists. It cannot run on the supported JDK
without modifying private library state.

Functor serialization tests verify the documented security rejection through
`SerializationUtils.serialize`. The clone-factory round trip explicitly opts in
for trusted in-memory data and restores the system property in `finally`; using
the restored factory exercises rediscovery of its transient clone method.
Configuration validation tests exercise invalid context, log, cache, and break
scope inputs, rather than asserting library defects.

`ClasspathResourceLoader.class$` only runs when classpath stream lookup throws
an exception. A missing resource returns `null` and takes a different path.
The resource-access policy test denies one template through the thread context
class loader and verifies that the public loader API preserves the denial as
the cause of `ResourceNotFoundException`. This resource-only policy delegates
class loading and does not define or introduce classes or use a security manager.

The JaCoCo XML omits synthetic `class$` methods, although the dynamic-access
report counts their calls. For example, the existing `MultiValueMap` constructor
and `StringResourceLoader` static initializer necessarily invoke those helpers.
Additional tests cover the default map decorator, boxed Boolean properties,
configured reference insertion, exception creation, and overload selection, but
the synthetic helper itself may still be reported as uncovered. The Velocity
class/resource fallback with a null context class loader is tested without
introducing a custom class loader.
