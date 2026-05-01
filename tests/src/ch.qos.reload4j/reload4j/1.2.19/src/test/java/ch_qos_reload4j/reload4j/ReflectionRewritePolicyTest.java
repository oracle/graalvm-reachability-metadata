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

    @Test
    void rewritesBeanMessageAndPropertiesThroughJavaBeanGetters() {
        ReflectionRewritePolicy policy = new ReflectionRewritePolicy();
        BeanMessage message = new BeanMessage();
        LoggingEvent source = new LoggingEvent(ReflectionRewritePolicyTest.class.getName(), LOGGER, Level.INFO, message,
                null);
        source.setProperty("name", "original-name");
        source.setProperty("retained", "original-property");

        LoggingEvent rewritten = policy.rewrite(source);

        assertThat(rewritten).isNotSameAs(source);
        assertThat(rewritten.getLoggerName()).isEqualTo(LOGGER.getName());
        assertThat(rewritten.getLevel()).isSameAs(Level.INFO);
        assertThat(rewritten.getMessage()).isEqualTo("rewritten log message");
        assertThat(rewritten.getProperties())
                .containsEntry("name", "bean-name")
                .containsEntry("count", 42)
                .containsEntry("active", true)
                .containsEntry("retained", "original-property");
    }

    public static final class BeanMessage {
        public String getMessage() {
            return "rewritten log message";
        }

        public String getName() {
            return "bean-name";
        }

        public int getCount() {
            return 42;
        }

        public boolean isActive() {
            return true;
        }
    }
}
