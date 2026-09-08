/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import com.oracle.svm.core.jdk.UnsupportedFeatureError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeImageSupportTest {

    /// Stands in for `com.oracle.svm.core.jdk.UnsupportedFeatureError`, which exists only
    /// inside a native image and so cannot be instantiated here.
    private static Error unsupportedFeatureError() {
        return new UnsupportedFeatureError("Classes cannot be defined at runtime");
    }

    @Test
    void returnsFalseForNull() {
        assertFalse(NativeImageSupport.isUnsupportedFeatureError(null));
    }

    @Test
    void returnsFalseForOutOfMemoryError() {
        assertFalse(NativeImageSupport.isUnsupportedFeatureError(new OutOfMemoryError()));
    }

    @Test
    void returnsFalseForStackOverflowError() {
        assertFalse(NativeImageSupport.isUnsupportedFeatureError(new StackOverflowError()));
    }

    @Test
    void recognizesTheUnsupportedFeatureErrorByName() {
        assertTrue(NativeImageSupport.isUnsupportedFeatureError(unsupportedFeatureError()));
    }

    @Test
    void runsTheActionAndReturnsWhenNothingFails() throws Exception {
        boolean[] executed = {false};
        NativeImageSupport.runToleratingUnsupportedFeature(() -> executed[0] = true);
        assertTrue(executed[0]);
    }

    @Test
    void toleratesTheUnsupportedFeatureErrorItself() throws Exception {
        NativeImageSupport.runToleratingUnsupportedFeature(() -> {
            throw unsupportedFeatureError();
        });
    }

    @Test
    void toleratesTheUnsupportedFeatureErrorInTheCauseChain() throws Exception {
        NativeImageSupport.runToleratingUnsupportedFeature(() -> {
            throw new ExceptionInInitializerError(new IllegalStateException(unsupportedFeatureError()));
        });
    }

    @Test
    void toleratesTheUnsupportedFeatureErrorPrintedBeforeTheCauseIsDiscarded() throws Exception {
        NativeImageSupport.runToleratingUnsupportedFeature(() -> {
            unsupportedFeatureError().printStackTrace(System.err);
            throw new LibraryError("[FAILED_TO_LOAD_NATIVE_LIBRARY] null");
        });
    }

    @Test
    void rethrowsALibraryErrorThatLeavesNoEvidence() {
        LibraryError thrown = assertThrows(LibraryError.class,
                () -> NativeImageSupport.runToleratingUnsupportedFeature(() -> {
                    throw new LibraryError("[FAILED_TO_LOAD_NATIVE_LIBRARY] null");
                }));
        assertSame(LibraryError.class, thrown.getClass());
    }

    @Test
    void rethrowsAnUnrelatedAssertionFailure() {
        assertThrows(AssertionError.class,
                () -> NativeImageSupport.runToleratingUnsupportedFeature(() -> {
                    throw new AssertionError("unrelated failure");
                }));
    }

    @Test
    void restoresTheOriginalErrorStream() throws Exception {
        java.io.PrintStream originalErr = System.err;
        NativeImageSupport.runToleratingUnsupportedFeature(() -> {
            throw unsupportedFeatureError();
        });
        assertSame(originalErr, System.err);
    }

    /// Stands in for a library error that discards the cause it was built from.
    private static final class LibraryError extends Error {
        private LibraryError(String message) {
            super(message);
        }
    }
}
