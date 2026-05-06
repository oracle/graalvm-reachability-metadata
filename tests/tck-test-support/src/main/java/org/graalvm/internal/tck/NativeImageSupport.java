/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

/// Utilities for handling Native Image runtime behavior in tests.
public final class NativeImageSupport {

    private static final String IMAGE_INFO = "org.graalvm.nativeimage.ImageInfo";
    private static final String UNSUPPORTED_FEATURE_ERROR =
            "com.oracle.svm.core.jdk.UnsupportedFeatureError";

    private NativeImageSupport() {
    }

    /// Returns true when the current code is executing inside a Native Image runtime.
    public static boolean isInNativeImageRuntime() {
        try {
            Class<?> imageInfoClass = Class.forName(IMAGE_INFO);
            return (boolean) imageInfoClass.getMethod("inImageRuntimeCode").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }

    /// Returns true when the error is an UnsupportedFeatureError thrown by
    /// Native Image for unsupported dynamic operations such as runtime class loading.
    public static boolean isUnsupportedFeatureError(Error error) {
        return error != null
                && UNSUPPORTED_FEATURE_ERROR.equals(error.getClass().getName());
    }
}
