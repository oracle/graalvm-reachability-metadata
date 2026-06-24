/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_lib_extra;

import org.graalvm.internal.tck.NativeImageSupport;

final class NativeImageDynamicClassLoadingSupport {
    private NativeImageDynamicClassLoadingSupport() {
    }

    static void rethrowIfNotNativeImageDynamicClassLoadingError(Throwable throwable) {
        if (!hasUnsupportedFeatureError(throwable) && !hasUnsupportedIsolatedClassLoadingFailure(throwable)) {
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (throwable instanceof Error error) {
                throw error;
            }
            throw new AssertionError(throwable);
        }
    }

    private static boolean hasUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean hasUnsupportedIsolatedClassLoadingFailure(Throwable throwable) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClassNotFoundException || current instanceof NoClassDefFoundError) {
                String message = current.getMessage();
                if (message != null && (message.startsWith("com.diffplug.spotless.extra.")
                        || message.startsWith("org.eclipse.")
                        || message.startsWith("org/eclipse/"))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
