# Test scope

The suite exercises filters, NLS message initialization, alias resources,
permission descriptors, resolver-state persistence, storage configuration,
hook discovery, diagnostics, splash updates, and the Equinox API before startup.
Each tested dynamic-access class has its own test file.

Full framework startup is not exercised: `Framework.initializeContextFinder()`
constructs `ContextFinder.Finder`, a `SecurityManager`, which JDK 25 disallows.
`MultiplexingFactory` and `BundleLoader` also construct `SecurityManager`
subclasses in static initializers. Tests neither enable legacy security-manager
support nor assert those failures as supported behavior.

Consequently the framework lifecycle, console command providers/interpreters,
console socket getter, application launcher restart, service registry and its
property cloning, internal system bundle, and multiplexing URL/content factories
remain uncovered. `EclipseStarter` also reaches framework initialization before
publishing its splash services. Conditional-permission evaluation needs a live
framework; `SecurityRowTest` covers only defensive copies of its descriptors.

Bundle activation, extension loading, `DefaultClassLoader`,
`EquinoxFWClassLoader`, and `CompositeClassLoader` loading paths require OSGi
class-loader machinery. They are not exercised by defining classes, loading
plugin JARs, or using test-side reflection. `SystemPolicyTest` delegates only to
an existing JDK class and a packaged resource, without defining any classes.
The privileged branches of `SecureAction` require an installed security manager
and remain uncovered. `SystemBundleData` manifest discovery likewise has no
standalone consumer entry point here without initializing framework storage.

Sources were inspected from the matching Eclipse source bundle because the
provided source context contained a repository HTML page:
`https://archive.eclipse.org/eclipse/updates/3.5/R-3.5-200906111540/plugins/org.eclipse.osgi.source_3.5.0.v20090520.jar`.
The artifact version appears here only to identify the inspected sources; test
inputs and assertions do not depend on it.
