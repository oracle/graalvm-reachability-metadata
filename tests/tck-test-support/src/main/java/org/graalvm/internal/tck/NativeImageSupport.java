/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
    /// appears in the cause chain, or when its trace reached `System.err` while the action
    /// ran — the last covers libraries that catch the error and re-throw their own without
    /// a cause. Every other failure is re-thrown unchanged, and on the JVM the action runs
    /// and asserts normally, so this is never a skip.
    ///
    /// The stderr capture replaces `System.err` for the duration of the action, so tests
    /// using it must not run concurrently with tests that assert on `System.err`.
    ///
    /// @throws Exception whatever the action throws
    public static void runToleratingUnsupportedFeature(ThrowingRunnable action) throws Exception {
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(new TeeOutputStream(capturedErr, originalErr), true,
                StandardCharsets.UTF_8));
        try {
            action.run();
        } catch (Error error) {
            if (!hasUnsupportedFeatureCause(error) && !printedUnsupportedFeature(capturedErr)) {
                throw error;
            }
        } finally {
            System.setErr(originalErr);
        }
    }

    private static boolean hasUnsupportedFeatureCause(Error error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof Error candidate && isUnsupportedFeatureError(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean printedUnsupportedFeature(ByteArrayOutputStream capturedErr) {
        return capturedErr.toString(StandardCharsets.UTF_8).contains(UNSUPPORTED_FEATURE_ERROR);
    }

    /// Keeps captured output visible on the original stream so a tolerated failure is
    /// still diagnosable from the test log.
    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream captured;
        private final OutputStream original;

        private TeeOutputStream(OutputStream captured, OutputStream original) {
            this.captured = captured;
            this.original = original;
        }

        @Override
        public void write(int b) throws IOException {
            captured.write(b);
            original.write(b);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            captured.write(bytes, offset, length);
            original.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            captured.flush();
            original.flush();
        }
    }
}
