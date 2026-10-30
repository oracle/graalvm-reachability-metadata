/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.io.IOException;

import org.apache.velocity.util.ExceptionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ExceptionUtilsTest {
    @Test
    void createsExceptionsAndAttachesTheirCauses() {
        IOException cause = new IOException("input unavailable");
        RuntimeException runtime = ExceptionUtils.createRuntimeException("rendering", cause);
        assertEquals("rendering", runtime.getMessage());
        assertSame(cause, runtime.getCause());

        Throwable typed = ExceptionUtils.createWithCause(IllegalArgumentException.class, "argument", cause);
        assertInstanceOf(IllegalArgumentException.class, typed);
        assertEquals("argument", typed.getMessage());
        assertSame(cause, typed.getCause());

        IOException target = new IOException("reading");
        ExceptionUtils.setCause(target, cause);
        assertSame(cause, target.getCause());

        Throwable messageOnly = ExceptionUtils.createWithCause(MessageException.class, "report", cause);
        assertInstanceOf(MessageException.class, messageOnly);
        assertEquals("report caused by " + cause, messageOnly.getMessage());
    }

    public static class MessageException extends Exception {
        private static final long serialVersionUID = 1L;

        public MessageException(String message) {
            super(message);
        }
    }
}
