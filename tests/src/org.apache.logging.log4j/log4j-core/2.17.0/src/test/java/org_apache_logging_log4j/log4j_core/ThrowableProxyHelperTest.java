/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThrowableProxyHelperTest {
    @Test
    void formatsAnExceptionAfterItsThrowingMethodHasReturned() {
        Throwable failure = captureFailure();
        IllegalStateException outer = new IllegalStateException("request failed", failure);
        outer.addSuppressed(new IllegalArgumentException("cleanup failed"));
        ThrowableProxy proxy = new ThrowableProxy(outer);
        String trace = proxy.getExtendedStackTraceAsString();
        assertTrue(trace.contains("request failed"));
        assertTrue(trace.contains("captureFailure"));
        assertTrue(trace.contains("cleanup failed"));
        assertEquals("invalid input", proxy.getCauseProxy().getMessage());
        assertEquals(1, proxy.getSuppressedProxies().length);
    }

    private static Throwable captureFailure() {
        try {
            throw new IllegalArgumentException("invalid input");
        } catch (IllegalArgumentException failure) {
            return failure;
        }
    }
}
