/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.commons.lang.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;

public class CommonsExceptionUtilsTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Throwable root = new IllegalArgumentException("root");
        LegacyException legacy = new LegacyException(root);
        assertSame(root, ExceptionUtils.getCause(legacy, new String[]{"getLinkedCause"}));
        assertSame(root, ExceptionUtils.getCause(new FieldException(root), new String[0]));
        assertTrue(ExceptionUtils.isNestedThrowable(legacy));
        WritableException target = new WritableException();
        assertTrue(ExceptionUtils.setCause(target, root));
        assertSame(root, target.getCause());
        assertSame(root, target.linked);
        assertSame(root, ExceptionUtils.getRootCause(new RuntimeException("outer", legacy)));
    }

    public static class LegacyException extends Exception {
        private final Throwable linked;

        public LegacyException(Throwable linked) {
            this.linked = linked;
        }

        public Throwable getLinkedCause() {
            return linked;
        }

        @Override
        public synchronized Throwable getCause() {
            return linked;
        }
    }

    public static class FieldException extends Exception {
        public Throwable detail;

        public FieldException(Throwable cause) {
            detail = cause;
        }
    }

    public static class WritableException extends Exception {
        private Throwable linked;

        public void setCause(Throwable cause) {
            linked = cause;
        }
    }
}
