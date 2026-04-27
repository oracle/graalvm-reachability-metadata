/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.rewrite.ReflectionRewritePolicy;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

public class ReflectionRewritePolicyTest {
    private static final Logger LOGGER = Logger.getLogger(ReflectionRewritePolicyTest.class);
    private static final String LOGGER_FQCN = ReflectionRewritePolicyTest.class.getName();

    @Test
    void rewritesBeanMessageWithReadableProperties() {
        BeanLogMessage message = new BeanLogMessage("rewritten log message", "customer-sync", 7, true);
        LoggingEvent source = new LoggingEvent(LOGGER_FQCN, LOGGER, Level.INFO, message, null);
        source.setProperty("operation", "stale-operation");
        source.setProperty("correlationId", "original-correlation-id");

        LoggingEvent rewritten = new ReflectionRewritePolicy().rewrite(source);

        assertThat(rewritten).isNotSameAs(source);
        assertThat(rewritten.getLoggerName()).isEqualTo(LOGGER.getName());
        assertThat(rewritten.getLevel()).isSameAs(Level.INFO);
        assertThat(rewritten.getMessage()).isEqualTo("rewritten log message");
        assertThat(rewritten.getProperty("operation")).isEqualTo("customer-sync");
        assertThat(rewritten.getProperty("attempt")).isEqualTo("7");
        assertThat(rewritten.getProperty("successful")).isEqualTo("true");
        assertThat(rewritten.getProperty("correlationId")).isEqualTo("original-correlation-id");
        assertThat(rewritten.getProperty("message")).isNull();
    }

    public static final class BeanLogMessage {
        private final String message;
        private final String operation;
        private final int attempt;
        private final boolean successful;

        public BeanLogMessage(String message, String operation, int attempt, boolean successful) {
            this.message = message;
            this.operation = operation;
            this.attempt = attempt;
            this.successful = successful;
        }

        public String getMessage() {
            return message;
        }

        public String getOperation() {
            return operation;
        }

        public int getAttempt() {
            return attempt;
        }

        public boolean isSuccessful() {
            return successful;
        }
    }
}
