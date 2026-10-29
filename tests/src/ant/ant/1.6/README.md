# Ant test coverage

Tests exercise existing classes through Ant's public APIs, without defining or loading new bytecode.
The per-class test files cover configuration, task adaptation, XML parsing and catalogs,
transformations, JDBC, subprocesses, selectors, manifests, and ZIP fields.

## Paths intentionally left uncovered

- `AntClassLoader.forceLoadClass`, class definition, and the package-definition path in
  `AntClassLoader2` introduce bytecode at runtime. Tests use parent delegation instead.
- `AntClassLoader.initializeClass` relies on invoking a constructor with invalid arguments
  and swallowing every throwable; it cannot demonstrate successful initialization reliably.
- `Javac12` requires the removed `sun.tools.javac.Main` compiler. `Kjc` requires the obsolete
  Kopi compiler. `Javac13` invokes the JDK compiler implementation rather than an application
  runtime API. These compiler executions are not exercised or replaced with fake classes.
- `SunRmic` execution requires the removed `sun.rmi.rmic.Main`; `KaffeRmic` requires Kaffe's
  compiler, and `WLRmic` requires WebLogic's compiler. Adapter discovery and filename mapping
  are exercised without attempting these unavailable compilers.
- `ClassConstants` converts class-file bytes through the platform's default text encoding
  (`readFully()` followed by `String.getBytes()`), which cannot round-trip a class file on
  the required UTF-8 JDK. No encoding override or assertion of the resulting failure is added.
- `ModifiedSelector`'s explicitly selected algorithm/cache/comparator branches construct
  names in `selectors.cacheselector`, while the shipped implementations are in
  `selectors.modifiedselector`. Tests exercise its working default implementations instead.
- `Get`'s authentication path first requires the removed `sun.misc.BASE64Encoder`. Its fallback
  encoder consumes two input bytes per three-byte loop iteration and overruns its output
  buffer for ordinary credentials. The working unauthenticated file download is tested instead.
- Deprecated Xalan/XSL:P processors are not supplied. The supported TraX processor is tested.

The suite does not skip Native Image or accept metadata failures. Metadata collection and native
verification belong to the subsequent workflow states.
