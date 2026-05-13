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
    private static final String RUNTIME_IMAGE_CODE = "runtime";

    private NativeImageSupport() {
    }

    /// Returns true when the throwable is an UnsupportedFeatureError thrown by
    /// Native Image for unsupported dynamic operations such as runtime class loading.
    public static boolean isUnsupportedFeatureError(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (UNSUPPORTED_FEATURE_ERROR.equals(throwable.getClass().getName())) {
            return true;
        }
        return isNativeImageRuntime() && isUnsupportedAotClassLoadingFailure(throwable);
    }

    /// Returns true when the code is running inside a Native Image runtime.
    public static boolean isNativeImageRuntime() {
        return RUNTIME_IMAGE_CODE.equals(System.getProperty(IMAGE_CODE_PROPERTY));
    }

    private static boolean isUnsupportedAotClassLoadingFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClassNotFoundException
                    && containsAotClassLoadingStack(current.getStackTrace())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsAotClassLoadingStack(StackTraceElement[] stackTrace) {
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if ("com.oracle.svm.core.hub.registry.AOTClassRegistry".equals(className)
                    || "com.oracle.svm.core.hub.registry.ClassRegistries".equals(className)
                    || "java.net.URLClassLoader".equals(className)) {
                return true;
            }
        }
        return false;
    }
}
