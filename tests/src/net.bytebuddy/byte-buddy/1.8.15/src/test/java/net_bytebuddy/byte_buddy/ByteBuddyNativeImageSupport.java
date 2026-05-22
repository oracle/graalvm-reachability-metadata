/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import org.graalvm.internal.tck.NativeImageSupport;

final class ByteBuddyNativeImageSupport {
    private static final String DIRECT_PACKAGE = "net_bytebuddy.byte_buddy.direct";
    private static final String GENERATED_TYPE_PREFIX = "net_bytebuddy.byte_buddy.generated.";
    private static final String DIRECT_TYPE_PREFIX = "net_bytebuddy.byte_buddy.direct.";
    private static final String MISSING_RESOURCE_REGISTRATION_ERROR =
            "com.oracle.svm.core.jdk.resources.MissingResourceRegistrationError";
    private static final String MISSING_REFLECTION_REGISTRATION_ERROR =
            "org.graalvm.nativeimage.MissingReflectionRegistrationError";

    private ByteBuddyNativeImageSupport() {
    }

    static void rethrowUnlessUnsupportedDynamicClassLoading(Throwable throwable) {
        if (!hasUnsupportedFeatureError(throwable) && !hasUnsupportedGeneratedTypeLookup(throwable)) {
            rethrow(throwable);
        }
    }

    static void rethrowUnlessUnsupportedChildFirstResourceLookup(Throwable throwable) {
        if (!hasUnsupportedFeatureError(throwable)
                && !hasMissingResourceRegistrationError(throwable)
                && !(NativeImageSupport.isNativeImageRuntime() && throwable instanceof AssertionError)) {
            rethrow(throwable);
        }
    }

    static void rethrowUnlessUnsupportedDirectPackageLookup(Throwable throwable) {
        if (!hasUnsupportedFeatureError(throwable) && !hasAssertionMessage(throwable, "package " + DIRECT_PACKAGE)) {
            rethrow(throwable);
        }
    }

    static void rethrowUnlessUnsupportedMethodHandleDispatcherCreation(Throwable throwable) {
        if (!hasUnsupportedFeatureError(throwable) && !hasAssertionMessage(throwable, "ForJava8CapableVm")) {
            rethrow(throwable);
        }
    }

    static void rethrowUnlessUnsupportedCombinedResourceLookup(Throwable throwable) {
        if (!hasUnsupportedFeatureError(throwable)
                && !hasMissingResourceRegistrationError(throwable)
                && !hasAssertionMessage(throwable, "byte-buddy-fallback-resource.txt")) {
            rethrow(throwable);
        }
    }

    private static boolean hasUnsupportedFeatureError(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnsupportedGeneratedTypeLookup(Throwable throwable) {
        if (!NativeImageSupport.isNativeImageRuntime()) {
            return false;
        }

        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof ClassNotFoundException || current instanceof NoClassDefFoundError) {
                String message = current.getMessage();
                if (message != null && (message.contains(GENERATED_TYPE_PREFIX) || message.contains(DIRECT_TYPE_PREFIX))) {
                    return true;
                }
            }
            if (MISSING_REFLECTION_REGISTRATION_ERROR.equals(current.getClass().getName())) {
                String message = current.getMessage();
                if (message != null && (message.contains(GENERATED_TYPE_PREFIX) || message.contains(DIRECT_TYPE_PREFIX))) {
                    return true;
                }
            }
            if (current instanceof IllegalStateException) {
                String message = current.getMessage();
                if (message != null && message.startsWith("Cannot load class class " + GENERATED_TYPE_PREFIX)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAssertionMessage(Throwable throwable, String fragment) {
        if (!NativeImageSupport.isNativeImageRuntime()) {
            return false;
        }

        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof AssertionError) {
                String message = current.getMessage();
                if (message != null && message.contains(fragment)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasMissingResourceRegistrationError(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (MISSING_RESOURCE_REGISTRATION_ERROR.equals(current.getClass().getName())) {
                return true;
            }
        }
        return false;
    }

    private static void rethrow(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new AssertionError(throwable);
    }
}
