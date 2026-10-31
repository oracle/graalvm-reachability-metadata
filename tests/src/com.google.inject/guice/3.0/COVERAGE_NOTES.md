# Public-API coverage boundaries

The dedicated tests exercise constructor and member injection, provider methods,
qualifiers, constant conversion, circular interface dependencies, generic array
types, typed SPI collections, diagnostic formatting, and the weak caches used by
injector construction. Cache initialization is exercised through Guice rather
than by importing its shaded utility classes.

`InjectionPointTest` also exercises the successful same-named backing-field
lookup in `InjectionPoint.checkForMisplacedBindingAnnotations`. It verifies
that SPI discovery accepts a qualified accessor with a backing field and that
member injection uses the field and parameter qualifiers, not the accessor's
annotation. The private accessor selects ordinary reflective invocation.

The following dynamic-access paths are intentionally not invoked directly:

- `CustomConcurrentHashMap.Impl` serialization and its `Fields` helper,
  `MapMaker.StrategyImpl` serialization and its `Fields` helper: Guice does not
  expose these internal cache objects as a serializable public-API value.
- `Finalizer` cleanup: there is no public Guice API to enqueue or drain its
  internal references deterministically. Tests do not force garbage collection
  or import the internal reference classes.
- `FinalizableReferenceQueue.SystemLoader` and `.DecoupledLoader`: ordinary
  cache initialization can visit these internally, but there is no public API
  selecting a loader. No test installs a custom class loader to force a branch.
- `BytecodeGen.BridgeClassLoader` for a type of the platform loader, such as a
  circular dependency on `javax.sql.CommonDataSource`: Guice proxies the type in
  a bridge parented on the platform loader, which reaches Guice's own classes by
  delegation. Native Image accepts a proxy loader only when the interfaces'
  common loader is that loader or one of its parents, so the proxy fails in a
  native image although the JVM accepts it:
  https://github.com/oracle/graal/issues/14849 (GR-81040). The tests use
  application interfaces instead.
- The shaded CGLIB `ReflectUtils`, `Enhancer`, `MethodProxy`,
  `AbstractClassGenerator`, `EmitUtils`, `KeyFactory`, `MethodWrapper`,
  `ReflectUtils.2`, `CallbackInfo`, `MethodInterceptorGenerator`, `FastClass`,
  and `FastClassEmitter`, and ASM `ClassWriter`: the consumer paths are bytecode
  generation for interception or optimized invocation. Tests use private
  injectable constructors and methods, which select ordinary reflection,
  rather than targeting runtime class definition or calling shaded internals.
  `DefaultConstructionProxyFactory` routes public constructors to
  `BytecodeGen.newFastClass`; `SingleMethodInjector` routes methods that are
  neither private nor protected there too. Interception instead reaches
  `ProxyFactory.ProxyConstructor`, which calls `Enhancer.createClass`. These
  are runtime class-definition paths, not missing registration scenarios.
  The downloaded source archive omits the shaded CGLIB and ASM sources;
  their available binary API was inspected alongside Guice's dispatch code.
- ASM `ClassReader(String)` is not the reader constructor used by Guice's
  diagnostic path. `LineNumbersTest` exercises the resource lookup and
  `ClassReader(InputStream)` that diagnostics actually use.

These boundaries follow the repository test contract in
`docs/functional-spec/test-contract.md`: use consumer APIs, do not bypass
internals with reflection or serialization, and do not target runtime class
definition. The measured report, not this list, determines achieved coverage.
