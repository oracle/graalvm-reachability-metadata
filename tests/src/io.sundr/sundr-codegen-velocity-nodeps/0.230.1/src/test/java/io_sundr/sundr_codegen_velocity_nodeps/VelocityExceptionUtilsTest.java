/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.velocity.util.ExceptionUtils;
import org.junit.jupiter.api.Test;

public class VelocityExceptionUtilsTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Throwable cause = new IllegalStateException("source");
        Throwable created = ExceptionUtils.createWithCause(IllegalArgumentException.class, "message", cause);
        assertEquals("message", created.getMessage());
        assertSame(cause, created.getCause());
        RuntimeException target = new RuntimeException("target");
        ExceptionUtils.setCause(target, cause);
        assertSame(cause, target.getCause());
        Throwable fallback = ExceptionUtils.createWithCause(MessageException.class, "legacy", cause);
        assertTrue(fallback.getMessage().contains("legacy"));
        assertTrue(fallback.getMessage().contains("source"));
    }

    public static class MessageException extends RuntimeException {
        public MessageException(String message) {
            super(message);
        }
    }
}
