# Post-generation intervention report

Library: org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2

Stage: metadata_fix_failed

## Summary

The generated JVM tests pass, but the native-image test execution fails when the tests exercise `kotlinx.coroutines.swing.Dispatchers.Swing` / `Dispatchers.Main`. The native executable fails while initializing the Swing/AWT X11 toolkit path used by `SwingUtilities.invokeLater` and `SwingUtilities.isEventDispatchThread`.

## Root cause by failing test

All generated test failures have the same root cause: native-image lacks complete JNI/reachability metadata for the AWT/Swing X11 bootstrap path reached by `kotlinx.coroutines.swing.Swing`.

- `swingDispatcherRunsCoroutineBodyOnEventDispatchThread` fails when `Dispatchers.Swing` initializes `SwingUtilities.invokeLater`, which triggers `Toolkit.getDefaultToolkit()` and `sun.awt.X11.XToolkit` initialization.
- `mainDispatcherIsProvidedBySwingFactoryAndDispatchesToEventDispatchThread` fails because the Main dispatcher factory delegates to the Swing dispatcher and the same `XToolkit` initialization fails.
- `immediateDispatcherRunsInlineOnlyWhenAlreadyOnEventDispatchThread` fails while checking `SwingUtilities.isEventDispatchThread`, again requiring the AWT event queue/toolkit.
- `asyncWorkComposesOnSwingDispatcher` fails because nested coroutines use `Dispatchers.Swing.immediate`, which depends on the same Swing dispatcher singleton.
- `swingDispatcherPostsNestedWorkInsteadOfRunningInlineOnEventDispatchThread` fails in the native run inside the `Dispatchers.Swing` posting path. The Gradle excerpt shows this test on the stack when the native executable exits non-zero.
- `delayResumesOnEventDispatchThreadAndCancellationStopsPendingContinuation` fails because delayed continuation resumption is scheduled through the Swing dispatcher.
- `timeoutScheduledOnSwingDispatcherCancelsSuspendedCoroutineOnEventDispatchThread` fails for the same reason; the expected timeout is replaced by Swing dispatcher initialization failure.

The Codex metadata-fix log shows that Codex was iterating on missing JNI-visible AWT internals. It added or investigated entries for `java.awt.GraphicsEnvironment`, `java.lang.System`, `sun.awt.SunToolkit`, `sun.awt.X11.XErrorHandlerUtil`, `sun.awt.X11.XToolkit`, and `sun.awt.X11.XBaseWindow`. The remaining failure still comes from that bootstrap chain, including `NoClassDefFoundError` around `sun.awt.X11.XToolkit` / `sun.awt.X11.XErrorHandlerUtil` and an intermediate `NoSuchMethodError` for JNI lookup of `java.lang.System.load(Ljava/lang/String;)V`. The log ends while another `nativeTest` run is in progress, so Codex did not complete the reproduce/fix/verify loop.

## Intervention decision

No generated test was removed. The failures are metadata-related rather than a test bug: the tests exercise real, relevant behavior of `kotlinx-coroutines-swing`, and the native-image failures point to incomplete or incorrect JNI metadata for the Swing/AWT X11 initialization path.

## Why the remaining generated support should be preserved

The generated support should be preserved because it validates the core purpose of `kotlinx-coroutines-swing`: binding coroutines to the Swing Event Dispatch Thread via `Dispatchers.Swing`, `Dispatchers.Swing.immediate`, and the Main dispatcher factory. Removing these tests would hide the unresolved reachability gap instead of documenting the missing native-image support that still needs to be completed.
