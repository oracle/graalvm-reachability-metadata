# AR-native-test-layers: Native test layer construction

Layered execution keeps library build policy consistent across bases and test
executables while preserving their separate classpaths, configuration, and
selectors (§FS-tests.3.2, §FS-tests.3.3, §FS-tests.3.4).
Artifact resolution, metadata preparation, and layer construction own separate
parts of that process; tracing and profiling provide test-only arguments rather
than changing base policy. The task overview is §AR-test-harness.3.

## 1. Shared JDK bases

Layered test tasks use `-Ptck.baseLayerDir=<dir>` or `GVM_TCK_BASE_LAYER_DIR`
when supplied; otherwise they default to `build/native-base-layer`. The base
layer directory contains `base-layer.nil` plus a manifest keyed by the exact
`native-image --version`, OS/architecture, native-image mode,
base-layer module set (`java.base`, `java.management`, `java.naming`, `java.sql`,
`jdk.unsupported`, `java.desktop`, `java.scripting`, `jdk.httpserver`,
`java.net.http`, `java.sql.rowset`, `jdk.jfr`, `java.smartcardio`,
`java.transaction.xa`, `java.security.sasl`, `java.xml`, `jdk.dynalink`,
`jdk.jsobject`, `jdk.localedata`, and `jdk.xml.dom`), and build arguments,
so a stale or mismatched layer is rejected before any per-coordinate native
image is built.

After a coordinate's build script is evaluated, the shared-layer lane compares
its library build policy with the common invocation policy (§FS-tests.3.3).
Additional options select a coordinate-local JDK-only base under that test
project's build directory. The base task runs before native compilation and
always rebuilds; it does not use the common layer cache. Its manifest and the
application's expected manifest use the same policy. Coordinates without extra
options continue to consume the common cached base. Shared bases omit third-party
security-provider registrations, which require the application's classpath;
JDK provider registrations remain in the base policy (§FS-tests.3.3).

## 2. Dedicated library bases

The dedicated-layer lane keeps the shared-layer lane intact but moves layer
creation into each coordinate build. Each layer starts with the shared layer's
JDK module set, omitting explicit selectors for optional modules whose classes
overlap dependency JARs (§FS-tests.3.4), and includes every class and resource
in the resolved tested-library JARs and the resolved JUnit runtime JARs, including JUnit's support artifacts
and the Native Build Tools `junit-platform-native` artifact that supplies
`JUnitPlatformFeature`. The base analysis classpath contains resolved dependency
JARs but excludes the coordinate's compiled test classes, test resources, and
test JAR. It filters the configured native-test classpath to resolved dependency
artifacts without changing their order (§FS-tests.3.7), so duplicate classes
resolve from the same dependency in both builds. Both layer builds activate
`JUnitPlatformFeature`. The base invocation
uses infrastructure-generated selectors for the JUnit engine roots, establishing
JUnit's initialization and reachability policy without trying to resolve absent
test classes. The application invocation uses the coordinate's real unique-ID
files to discover and register those classes. The written test code therefore
remains exclusively in the final application layer.
The base build uses the same resolved Native Image configuration directories as
the final test image. For both builds, it stages test-scoped
`reachability-metadata.json` without conditions because LayerCreate does not
preserve runtime `typeReached` tracking for selected JAR types. The normal test
lane still validates the original conditions; the dedicated lane applies the
same metadata unconditionally in both analyses to keep class-initialization
policy stable without including test classes or unrelated test resources in the
base layer.
The dedicated base also receives the coordinate's test-binary build options
(§FS-tests.3.2). After the coordinate build script has configured both binaries,
the base lazily reuses the test-binary policy in its original order. Settings
from `binaries.all` occur once because the policy is reused, not appended.
Harness-owned test instrumentation,
metadata arguments, and layer wiring are separated from that policy; the base
keeps its own generated configuration and engine-root JUnit selectors.
Gradle rebuilds the layer when its coordinate, Native Image settings,
configuration, or base-analysis classpath changes. The final test image retains
the complete standalone test runtime classpath while using the
coordinate-specific layer.
Module selection is evaluated lazily from the configured base classpath and
the JDK selected for that native build. It compares dependency class names with
that JDK's module contents, retains `java.base`, and reports the optional modules
omitted. It changes neither dependency files nor Native Image validation.
CI supplies `-Ptck.layered.deleteDedicatedLayerAfterTest=true` to delete each
large coordinate layer after its test; local runs retain layers for reuse unless
they explicitly request the same cleanup behavior.

`testSharedLayer` can run a coordinate batch in collecting mode with
`-Ptck.layered.continueOnCoordinateFailure=true`; when combined with
`-Ptck.layered.coordinateFailureReport=<file>`, it writes one failed coordinate
per line before failing the task at the end of the batch.

The manual layered workflow supplies `-Ptck.excludedCoordinatesFile` with the
shared-layer residual-failure list to the shared lane and the dedicated-layer
residual-failure list to the dedicated lane. This keeps known failures from
hiding new failures while allowing each lane's failures to be triaged
independently. Other workflows do not supply an exclusion file.

## 3. Runtime library packaging

The successful application compilation finishes by copying missing `.so`,
`.dylib`, and `.dll` files from the selected base's directory beside the test
executable (§FS-tests.3.5). This follows the existing layered JCK runner's
packaging approach. Application-produced files take precedence over base files.
Native Build Tools cleans its output directory before rebuilding and tracks
that directory as a Gradle output; the copy runs before output snapshots are
captured. Tracking the base directory as an input also invalidates the
application when its runtime libraries change. All consumers of the built
executable receive the same packaged directory.
