/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

final class NativeImageTestSupport {
    private static final String IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";
    private static final String NATIVE_IMAGE_RUNTIME = "runtime";
    private static final String UNSUPPORTED_RUNTIME_CLASS_DEFINITION_MESSAGE =
            "Defining new classes at runtime is not supported";

    private NativeImageTestSupport() {
    }

    static boolean isNativeImageRuntime() {
        return NATIVE_IMAGE_RUNTIME.equals(System.getProperty(IMAGE_CODE_PROPERTY));
    }

    static boolean hasUnsupportedRuntimeClassDefinitionCause(Throwable throwable, String... missingClassNames) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UnsupportedOperationException
                    && current.getMessage() != null
                    && current.getMessage().contains(UNSUPPORTED_RUNTIME_CLASS_DEFINITION_MESSAGE)) {
                return true;
            }
            if (isNativeImageRuntime()
                    && current instanceof ClassNotFoundException
                    && matchesMissingClassName(current.getMessage(), missingClassNames)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean matchesMissingClassName(String message, String[] missingClassNames) {
        if (message == null) {
            return false;
        }
        for (String missingClassName : missingClassNames) {
            if (message.equals(missingClassName)) {
                return true;
            }
        }
        return false;
    }
}
