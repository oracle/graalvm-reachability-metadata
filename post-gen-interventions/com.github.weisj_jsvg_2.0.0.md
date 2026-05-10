# Post-generation intervention report

Library: com.github.weisj:jsvg:2.0.0

Stage: `metadata_fix_failed`

## Summary

The generated `jsvg` native tests still fail during `nativeTest` because the native image cannot initialize the Java2D `BufferedImage` surface pipeline. The first failure is:

```text
java.lang.NoSuchMethodError: sun.awt.image.BufImgSurfaceData$ICMColorData.<init>(J)V
```

from `sun.awt.image.BufImgSurfaceData.initIDs(Native Method)`, reached through `BufferedImage.createGraphics()`. The remaining failures are follow-on `NoClassDefFoundError: Could not initialize class sun.awt.image.BufImgSurfaceData` errors after that class initialization failed.

## Root cause by failing test

| Test | Root cause | Classification |
| --- | --- | --- |
| `loadsStyledGeometryDefinitionsAndComputesShape()` | Calls `render(...)`, which creates a `Graphics2D` via `BufferedImage.createGraphics()`. The Java2D native initialization attempts to look up `sun.awt.image.BufImgSurfaceData$ICMColorData.<init>(long)` and fails with `NoSuchMethodError`. | Metadata-related missing JNI/reflection registration. |
| `loadsExternalImageResourcesRelativeToDocumentUrl(Path)` | Creates a `Graphics2D` for a temporary PNG and later renders the SVG. It hits the already-failed `BufImgSurfaceData` initialization. | Same metadata-related root cause. |
| `rendersPatternPaintWithRepeatedTiles()` | Calls `render(...)`; `BufferedImage.createGraphics()` reaches the failed `BufImgSurfaceData` initialization. | Same metadata-related root cause. |
| `exposesAnimationStateAndHonorsDocumentLimits()` | Creates a `Graphics2D` for an animation render target; `BufferedImage.createGraphics()` reaches the failed `BufImgSurfaceData` initialization. | Same metadata-related root cause. |
| `rendersMasksWithPartialTransparency()` | Calls `render(...)`; `BufferedImage.createGraphics()` reaches the failed `BufImgSurfaceData` initialization. | Same metadata-related root cause. |
| `rendersGradientClippingAndTransforms()` | Calls `render(...)`; `BufferedImage.createGraphics()` reaches the failed `BufImgSurfaceData` initialization. | Same metadata-related root cause. |

## Codex metadata-fix findings

The Codex metadata-fix log shows that the failure initially exposed several missing AWT/Java2D registrations. Codex added a broad AWT/Java2D reachability slice scoped to `com.github.weisj.jsvg.renderer.output.impl.Graphics2DOutput` and progressed the failure through earlier missing entries such as `IndexColorModel` and `Color.getRGB()`.

Codex did not finish the metadata loop. The current unresolved item is still in the Java2D image-surface JNI path: `sun.awt.image.BufImgSurfaceData$ICMColorData.<init>(J)V`. This indicates incomplete JNI-accessible metadata for the internal Java2D `BufImgSurfaceData`/`ICMColorData` surface initialization path rather than a bad generated test.

## Intervention decision

No generated tests were removed.

The failures are metadata-related: every failed test reaches the same `BufferedImage.createGraphics()` Java2D path, and the Gradle output reports missing JNI method lookup behavior rather than an assertion failure, unsupported test fixture, or platform-specific feature outside the library’s intended native support. Removing these tests would hide the remaining incomplete AWT/Java2D metadata for `jsvg` rendering.

## Why the remaining generated support should be preserved

The generated support exercises meaningful `jsvg` behavior: SVG parsing, styled geometry, gradients, clipping, masks, patterns, external image resource resolution, animation state, document limits, and rendering through the library’s `Graphics2D` output path. Codex already made progress on real missing reachability metadata, so the tests remain useful coverage for completing native-image support. Preserving them keeps the unresolved metadata gap visible for a future metadata-fix pass while retaining coverage for core `jsvg` functionality.
