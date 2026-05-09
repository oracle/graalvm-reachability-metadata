/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.janino;

import java.util.Set;

import org.graalvm.internal.tck.NativeImageSupport;

final class JaninoNativeImageSupport {
    private static final Set<String> GENERATED_CLASS_NAMES =
            Set.of("SC", "GeneratedFastClassBody", "JaninoMainEntry");

    private JaninoNativeImageSupport() {
    }

    static void rethrowIfNotNativeImageDynamicClassLoadingFailure(Throwable throwable) {
        if (!hasUnsupportedFeatureError(throwable) && !hasUnsupportedGeneratedClassLoadingFailure(throwable)) {
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (throwable instanceof Error error) {
                throw error;
            }
            throw new AssertionError(throwable);
        }
    }

    static void rethrowIfNotNativeImageJrtUrlAccessFailure(Throwable throwable) {
        if (!hasUnsupportedJrtUrlAccessFailure(throwable)) {
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

    private static boolean hasUnsupportedGeneratedClassLoadingFailure(Throwable throwable) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClassNotFoundException || current instanceof NoClassDefFoundError) {
                String message = current.getMessage();
                if (message != null && GENERATED_CLASS_NAMES.contains(message)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean hasUnsupportedJrtUrlAccessFailure(Throwable throwable) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("JavaRuntimeURLConnection not available")
                    || message.contains("URL protocol jrt"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
