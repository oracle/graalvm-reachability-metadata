# Dynamic-access coverage limits

`FastClassTest` uses the public `FastClass.Generator` attempt-load API to reuse
an application-owned, statically compiled accessor. It checks constructor and
method lookup, invocation, and missing-member errors without defining classes.

The following targets cannot be exercised through a supported public API
without runtime class definition. They remain uncovered rather than introducing
native-only skips, unsupported-feature tolerance, or replacement classes in
library packages (`FS-test-contract.2.2`, `FS-test-contract.2.3`, and
`FS-test-contract.4.5`).

- `net.sf.cglib.core.MethodWrapper`: initializing the class calls
  `KeyFactory.create(MethodWrapperKey.class)` before `create` or `createSet`
  can run.
- `net.sf.cglib.proxy.CallbackInfo`: this package-private class is reached
  through `Enhancer`, whose static initializer creates its own key factory.
- `net.sf.cglib.proxy.Mixin`: its static initializer creates `MixinKey` through
  `KeyFactory.create`; this also precedes the otherwise usable `getClasses` API.
- `net.sf.cglib.proxy.MixinEmitter`: the public route is
  `Mixin.Generator.generateClass`. Initializing that generator accesses the
  enclosing `Mixin` class's synthetic class-literal field, initializing its
  key factory. Processing methods also calls `MethodWrapper.create`.
- `net.sf.cglib.proxy.MixinEverythingEmitter`: the public route uses the same
  generator with `STYLE_EVERYTHING` and encounters the same initialization and
  method-wrapper requirements.
- `net.sf.cglib.reflect.ConstructorDelegate`: its static initializer creates
  a constructor key factory before any delegate can be created.
- `net.sf.cglib.reflect.ConstructorDelegate$Generator`: even invoking
  `generateClass` directly for argument validation initializes the enclosing
  class. The published JAR's generator initializer reads
  `ConstructorDelegate.class$net$sf$cglib$reflect$ConstructorDelegate` before
  constructing its `Source`; the enclosing class creates its key factory.
- `net.sf.cglib.reflect.FastClassEmitter`: ordinary method collection applies
  `DuplicatesPredicate`, which calls `MethodWrapper.create` before the target
  `getDeclaredConstructors` call. Using a precompiled accessor avoids the
  emitter rather than covering it.
- `net.sf.cglib.reflect.MethodDelegate`: its static initializer creates a
  method-delegate key factory. Its generator's enclosing-class access also
  initializes that factory before direct emission or validation.
- `net.sf.cglib.util.StringSwitcher`: its static initializer creates a switcher
  key factory. Its generator also initializes the enclosing class before it
  can emit a switch.

For these key factories, `KeyFactory.create` uses its own generator and default
naming policy. It attempts to load the generated implementation, but the library
JAR contains no such implementations. It then follows
`AbstractClassGenerator.create` through `strategy.generate` to
`ReflectUtils.defineClass`. Configuring an outer generator to attempt-load a
precompiled application class does not replace this independently created key
factory. No metadata registration removes that class-definition requirement.
