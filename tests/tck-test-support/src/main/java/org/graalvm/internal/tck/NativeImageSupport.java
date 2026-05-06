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
    private static final String ATTACH_NOT_SUPPORTED_EXCEPTION =
            "com.sun.tools.attach.AttachNotSupportedException";
    private static final String AGENT_LOAD_EXCEPTION =
            "com.sun.tools.attach.AgentLoadException";
    private static final String AGENT_ATTACH_NOT_READY_MESSAGE =
            "attach handshake";
    private static final String AGENT_LOAD_UNSUPPORTED_MESSAGE =
            "Only jcmd is supported currently";

    private NativeImageSupport() {
    }

    /// Returns true when the throwable chain represents a Native Image runtime
    /// limitation, including unsupported dynamic agent attachment paths.
    public static boolean isUnsupportedFeature(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error
                    && UNSUPPORTED_FEATURE_ERROR.equals(error.getClass().getName())) {
                return true;
            }
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (ATTACH_NOT_SUPPORTED_EXCEPTION.equals(className)
                    && message != null
                    && message.contains(AGENT_ATTACH_NOT_READY_MESSAGE)) {
                return true;
            }
            if (AGENT_LOAD_EXCEPTION.equals(className)
                    && message != null
                    && message.contains(AGENT_LOAD_UNSUPPORTED_MESSAGE)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /// Returns true when the error is an UnsupportedFeatureError thrown by
    /// Native Image for unsupported dynamic operations such as runtime class loading.
    public static boolean isUnsupportedFeatureError(Error error) {
        return isUnsupportedFeature(error);
    }
}
