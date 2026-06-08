/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;

import org.apache.velocity.util.ExceptionUtils;
import org.junit.jupiter.api.Test;

public class ExceptionUtilsTest {
    @Test
    public void createsThrowablesAndSetsCausesThroughPublicHelpers() throws Exception {
        resetExceptionUtilsState();
        try {
            IOException rootCause = new IOException("root cause");

            RuntimeException runtimeException = ExceptionUtils.createRuntimeException(
                    "runtime wrapper", rootCause);

            assertThat(runtimeException).hasMessage("runtime wrapper");
            assertThat(runtimeException.getCause()).isSameAs(rootCause);

            Exception target = new Exception("target");
            IllegalStateException assignedCause = new IllegalStateException("assigned cause");

            ExceptionUtils.setCause(target, assignedCause);

            assertThat(target.getCause()).isSameAs(assignedCause);

            Throwable messageOnlyException = ExceptionUtils.createWithCause(
                    MessageOnlyException.class, "message only wrapper", rootCause);

            assertThat(messageOnlyException).isInstanceOf(MessageOnlyException.class);
            assertThat(messageOnlyException.getMessage())
                    .contains("message only wrapper")
                    .contains(rootCause.toString());
        } finally {
            resetExceptionUtilsState();
        }
    }

    private static void resetExceptionUtilsState() throws Exception {
        setStaticField("causesAllowed", true);
        setStaticField("class$java$lang$RuntimeException", null);
        setStaticField("class$java$lang$String", null);
        setStaticField("class$java$lang$Throwable", null);
    }

    private static void setStaticField(String fieldName, Object value) throws Exception {
        Field field = ExceptionUtils.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    public static final class MessageOnlyException extends Exception {
        private static final long serialVersionUID = 1L;

        public MessageOnlyException(String message) {
            super(message);
        }
    }
}
