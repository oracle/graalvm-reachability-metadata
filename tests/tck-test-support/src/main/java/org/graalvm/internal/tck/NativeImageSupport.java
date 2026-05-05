/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

/// Utilities for handling Native Image runtime behavior in tests.
public final class NativeImageSupport {

    private static final String NATIVE_IMAGE_CODE_PROPERTY =
            "org.graalvm.nativeimage.imagecode";
    private static final String UNSUPPORTED_FEATURE_ERROR =
            "com.oracle.svm.core.jdk.UnsupportedFeatureError";

    private NativeImageSupport() {
    }

    /// Returns true when the error is an UnsupportedFeatureError thrown by
    /// Native Image for unsupported dynamic operations such as runtime class loading.
    public static boolean isUnsupportedFeatureError(Error error) {
        return error != null
                && UNSUPPORTED_FEATURE_ERROR.equals(error.getClass().getName());
    }

    /// Returns true when running inside a Native Image runtime executable.
    public static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty(NATIVE_IMAGE_CODE_PROPERTY));
    }
}
