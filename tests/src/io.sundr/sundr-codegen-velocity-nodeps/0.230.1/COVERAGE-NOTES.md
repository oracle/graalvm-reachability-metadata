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

Some synthetic class lookups occur only in unsafe functor serialization guards,
legacy-JDK fallback branches, or error-reporting paths. Normal public API tests
do not force those branches by changing global library state.
