/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/// Utilities for handling Native Image runtime behavior in tests.
public final class NativeImageSupport {

    private static final String UNSUPPORTED_FEATURE_ERROR =
            "com.oracle.svm.core.jdk.UnsupportedFeatureError";

    private NativeImageSupport() {
    }

    /// An action that a test runs under [#runToleratingUnsupportedFeature].
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    /// Returns true when the error is an UnsupportedFeatureError thrown by
    /// Native Image for unsupported dynamic operations such as runtime class loading.
    public static boolean isUnsupportedFeatureError(Error error) {
        return error != null
                && UNSUPPORTED_FEATURE_ERROR.equals(error.getClass().getName());
    }

    /// Runs an action that fundamentally requires open-ended dynamic class loading and
    /// tolerates the failure only when Native Image is proven to have refused an operation.
    ///
    /// Proof is GraalVM's own `UnsupportedFeatureError`, never the library's error type,
    /// message, or stack frames. It is accepted when the thrown error is one, when one
    /// appears in the cause chain, or when a throwable carrying one was printed to
    /// `System.err` while the action ran — the last covers libraries that catch the error
    /// and re-throw their own without a cause. Every other failure is re-thrown unchanged,
    /// and on the JVM the action runs and asserts normally, so this is never a skip.
    ///
    /// An [AssertionError] or [VirtualMachineError] is always re-thrown, so a genuine
    /// assertion failure after a tolerated one is never masked.
    ///
    /// Printed throwables are inspected as objects rather than matched as text, so a
    /// library that renders the trace itself and prints the result as a string is not
    /// detected; that case is §FS-test-contract.4.3.2, not a failure to tolerate.
    ///
    /// `System.err` is replaced for the duration of the action, so tests using it must not
    /// run concurrently with tests that assert on `System.err`.
    ///
    /// @throws Exception whatever the action throws
    public static void runToleratingUnsupportedFeature(ThrowingRunnable action) throws Exception {
        PrintStream originalErr = System.err;
        UnsupportedFeatureTrackingPrintStream trackingErr =
                new UnsupportedFeatureTrackingPrintStream(originalErr);
        System.setErr(trackingErr);
        try {
            action.run();
        } catch (Error error) {
            if (mustAlwaysRethrow(error)
                    || (!hasUnsupportedFeatureCause(error) && !trackingErr.printedUnsupportedFeature())) {
                throw error;
            }
        } finally {
            System.setErr(originalErr);
        }
    }

    private static boolean hasUnsupportedFeatureCause(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof Error candidate && isUnsupportedFeatureError(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mustAlwaysRethrow(Error error) {
        return error instanceof AssertionError
                || error instanceof VirtualMachineError;
    }

    /// Records actual unsupported-feature throwables while keeping their traces visible.
    private static final class UnsupportedFeatureTrackingPrintStream extends PrintStream {
        private volatile boolean printedUnsupportedFeature;

        private UnsupportedFeatureTrackingPrintStream(PrintStream original) {
            super(original, true, StandardCharsets.UTF_8);
        }

        @Override
        public void println(Object value) {
            if (value instanceof Throwable throwable && hasUnsupportedFeatureCause(throwable)) {
                printedUnsupportedFeature = true;
            }
            super.println(value);
        }

        private boolean printedUnsupportedFeature() {
            return printedUnsupportedFeature;
        }
    }
}
