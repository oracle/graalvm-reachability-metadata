/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeImageSupportTest {

    @Test
    void returnsFalseForNull() {
        assertFalse(NativeImageSupport.isUnsupportedFeatureError(null));
    }

    @Test
    void returnsFalseForOutOfMemoryError() {
        assertFalse(NativeImageSupport.isUnsupportedFeatureError(new OutOfMemoryError()));
    }

    @Test
    void returnsFalseForStackOverflowError() {
        assertFalse(NativeImageSupport.isUnsupportedFeatureError(new StackOverflowError()));
    }

    @Test
    void returnsTrueForAttachHandshakeFailures() {
        Throwable throwable = new ExceptionInInitializerError(
                new com.sun.tools.attach.AttachNotSupportedException(
                        "pid state is not ready to participate in attach handshake!"));
        assertTrue(NativeImageSupport.isUnsupportedFeature(throwable));
    }

    @Test
    void returnsTrueForAttachAgentLoadFailures() {
        Throwable throwable = new ExceptionInInitializerError(
                new com.sun.tools.attach.AgentLoadException(
                        "Failed to load agent library: Invalid Operation. Only jcmd is supported currently."));
        assertTrue(NativeImageSupport.isUnsupportedFeature(throwable));
    }
}
