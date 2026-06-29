/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.janino;

import java.util.NoSuchElementException;

import org.graalvm.internal.tck.NativeImageSupport;

final class NativeImageDynamicClassLoadingSupport {
    private static final String NATIVE_IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";
    private static final String NATIVE_IMAGE_RUNTIME = "runtime";
    private static final String ABSTRACT_COMPILER_RESOURCE_FINDER = "org.codehaus.janino.AbstractCompiler$1";
    private static final String FIND_RESOURCE_METHOD = "findResource";

    private NativeImageDynamicClassLoadingSupport() {
    }

    static void rethrowIfNotNativeImageDynamicClassLoadingFailure(Throwable throwable) throws Exception {
        if (hasUnsupportedFeatureError(throwable)
                || hasUnsupportedGeneratedClassLoadingFailure(throwable)
                || hasUnsupportedRuntimeClassPathFailure(throwable)) {
            return;
        }

        if (throwable instanceof Exception exception) {
            throw exception;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new AssertionError(throwable);
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
        if (!isNativeImageRuntime()) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClassNotFoundException || current instanceof NoClassDefFoundError) {
                String message = current.getMessage();
                if ("GeneratedMain".equals(message) || "SC".equals(message)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean hasUnsupportedRuntimeClassPathFailure(Throwable throwable) {
        if (!isNativeImageRuntime()) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NoSuchElementException && hasAbstractCompilerFindResourceStackTrace(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean hasAbstractCompilerFindResourceStackTrace(Throwable throwable) {
        for (StackTraceElement stackTraceElement : throwable.getStackTrace()) {
            if (ABSTRACT_COMPILER_RESOURCE_FINDER.equals(stackTraceElement.getClassName())
                    && FIND_RESOURCE_METHOD.equals(stackTraceElement.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNativeImageRuntime() {
        return NATIVE_IMAGE_RUNTIME.equals(System.getProperty(NATIVE_IMAGE_CODE_PROPERTY));
    }
}
