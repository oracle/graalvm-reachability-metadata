# AR-ci-composite-actions: Composite actions the CI workflows reuse

The composite actions under `.github/actions/` that the workflows of
§AR-repository-ci share, one citable declaration each. Each action carries a
`§`-citation back to its declaration here, for the CI that
§FS-repository-functional-spec.4.3 enumerates.

## AR-detect-file-changes: detect-file-changes action

A `node20` action that returns `changed: true/false` for whether any file
matching the given glob `file-patterns` changed in the current PR (supports
`**`, `*`, `?`, and `!` negation). Every PR-validation workflow gates its real
steps on this output so unrelated PRs cost almost nothing.
Serves §FS-repository-functional-spec.4.3 — every PR-scoped workflow there gates on it.

## AR-setup-native-build-tools: setup-native-build-tools action

A composite action that publishes and uses a native-build-tools (NBT) snapshot
when a branch with the same name as the caller's branch exists in the NBT
repository (or when `enabled-by-default` is set). It checks out that NBT ref,
reads `nativeBuildTools` from its `libs.versions.toml`, publishes it to
`mavenLocal`, and updates the caller repo's catalog to match — so a PR can be
tested against an in-progress NBT change just by matching branch names.
Serves §FS-repository-functional-spec.4.3 — the native-build-tools ref the CI lanes
build against.

## AR-setup-native-image-base-layer: setup-native-image-base-layer action

A composite action that restores or builds the shared JDK-module Native Image
base layer used by the layered TCK lane (§AR-test-harness.3). Its cache key is
derived from the actual installed `native-image --version`, runner
OS/architecture, selected native-image mode, `ci.json`, and TCK build
logic inputs. The action exports `GVM_TCK_BASE_LAYER_DIR` so subsequent Gradle
invocations in the manual layered workflow consume the exact cached layer
directory.
Serves §FS-repository-functional-spec.5.4 — the shared base layer belongs to the mode
matrix.

## AR-setup-crema-jdk: setup-crema-jdk action

A composite action that turns a downloaded GraalVM into a Crema JDK. GraalVM
early-access builds ship the `jvm-library` native-image macro and
`lib/graalvm/svm-libjvm.jar` but deliberately omit the built library, so the
action runs `native-image --macro:jvm-library`, which writes `lib/svm/libjvm.so`
— the Substrate VM with `-H:+RuntimeClassLoading` enabled. All Crema build
options come from the macro and from `svm-libjvm.jar`'s own
`native-image.properties`; the action adds none.
Serves §FS-repository-functional-spec.5.4 — the Crema JDK is a Native Image mode input.

It then rewrites `lib/jvm.cfg` to list `-svm` first, making the Substrate VM the
JDK's default so that a plain `java` invocation is Crema and no caller needs a VM
selection flag. The original file is kept as `jvm.cfg.hotspot-default` and
restored when the `default-vm` input is `hotspot`, which yields a byte-identical
tree running the stock VM — the baseline half of §AR-test-all-metadata-crema.
The action fails if the macro is absent from the downloaded JDK, because a
silently-HotSpot run would report a meaningless clean pass.
