/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

/// Utilities for handling Native Image runtime behavior in tests.
public final class NativeImageSupport {

    private static final String UNSUPPORTED_FEATURE_ERROR =
            "com.oracle.svm.core.jdk.UnsupportedFeatureError";
    private static final String IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";
    private static final String IMAGE_CODE_RUNTIME = "runtime";

    private NativeImageSupport() {
    }

    /// Returns true when the error is an UnsupportedFeatureError thrown by
    /// Native Image for unsupported dynamic operations such as runtime class loading.
    public static boolean isUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (UNSUPPORTED_FEATURE_ERROR.equals(current.getClass().getName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /// Returns true when the code is executing inside a Native Image runtime.
    public static boolean isNativeImageRuntime() {
        return IMAGE_CODE_RUNTIME.equals(System.getProperty(IMAGE_CODE_PROPERTY));
    }
}
