/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.internal.NativeLibraryLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NativeLibraryLoader$1Test {
    @Test
    void loadWithApplicationClassLoaderInvokesNativeLibraryUtilReflectivelyBeforeReportingMissingLibrary() {
        ClassLoader classLoader = NativeLibraryLoader$1Test.class.getClassLoader();
        Assertions.assertNotNull(classLoader, "Expected an application class loader for netty-common tests");

        UnsatisfiedLinkError error = Assertions.assertThrows(
                UnsatisfiedLinkError.class,
                () -> NativeLibraryLoader.load("metadata_forge_missing_native_library_helper_lookup", classLoader)
        );

        Assertions.assertTrue(
                error.getMessage().contains("could not load a native library"),
                () -> "Unexpected error message: " + error.getMessage()
        );
        Assertions.assertTrue(
                containsNestedUnsatisfiedLinkError(error),
                () -> "Expected helper invocation failure to be preserved in the exception chain: " + error
        );
    }

    private static boolean containsNestedUnsatisfiedLinkError(Throwable throwable) {
        return containsNestedUnsatisfiedLinkError(throwable, true);
    }

    private static boolean containsNestedUnsatisfiedLinkError(Throwable throwable, boolean skipCurrent) {
        if (throwable == null) {
            return false;
        }
        if (!skipCurrent && throwable instanceof UnsatisfiedLinkError) {
            return true;
        }
        for (Throwable suppressed : throwable.getSuppressed()) {
            if (containsNestedUnsatisfiedLinkError(suppressed, false)) {
                return true;
            }
        }
        return containsNestedUnsatisfiedLinkError(throwable.getCause(), false);
    }
}
