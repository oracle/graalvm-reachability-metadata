/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.apache.velocity.util.ExceptionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExceptionUtilsTest {
    @BeforeEach
    public void enableCauseSupport() throws Exception {
        setCausesAllowed(true);
    }

    @AfterEach
    public void restoreCauseSupport() throws Exception {
        setCausesAllowed(true);
    }

    @Test
    public void createsThrowablesUsingModernAndLegacyCausePaths() {
        IllegalArgumentException cause = new IllegalArgumentException("bad input");

        RuntimeException runtimeException = ExceptionUtils.createRuntimeException(
                "runtime failure", cause);
        assertThat(runtimeException)
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("runtime failure");
        assertThat(runtimeException.getCause()).isSameAs(cause);

        Exception target = new Exception("target");
        ExceptionUtils.setCause(target, cause);
        assertThat(target.getCause()).isSameAs(cause);

        Throwable fallbackException = ExceptionUtils.createWithCause(
                MessageOnlyException.class, "message-only failure", cause);
        assertThat(fallbackException)
                .isInstanceOf(MessageOnlyException.class)
                .hasMessageContaining("message-only failure")
                .hasMessageContaining("caused by")
                .hasMessageContaining(cause.toString());
        assertThat(fallbackException.getCause()).isNull();
    }

    private static void setCausesAllowed(boolean causesAllowed) throws Exception {
        Field field = ExceptionUtils.class.getDeclaredField("causesAllowed");
        field.setAccessible(true);
        field.setBoolean(null, causesAllowed);
    }

    public static final class MessageOnlyException extends Exception {
        private static final long serialVersionUID = 1L;

        public MessageOnlyException(String message) {
            super(message);
        }
    }
}
