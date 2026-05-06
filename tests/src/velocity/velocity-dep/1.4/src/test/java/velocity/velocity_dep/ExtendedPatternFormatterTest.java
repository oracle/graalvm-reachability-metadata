/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.log.ContextMap;
import org.apache.log.Hierarchy;
import org.apache.log.LogEvent;
import org.apache.log.LogTarget;
import org.apache.log.Logger;
import org.apache.log.format.ExtendedPatternFormatter;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExtendedPatternFormatterTest {
    @Test
    @Order(1)
    void formatsCallerMethodPatternThroughLogger() {
        final ExtendedPatternFormatter formatter = new ExtendedPatternFormatter("method=%{method};message=%{message}");
        final CapturingTarget target = new CapturingTarget(formatter);
        final Hierarchy hierarchy = new Hierarchy();
        hierarchy.setDefaultLogTarget(target);
        final Logger logger = hierarchy.getLoggerFor("formatter.category");

        logger.info("formatter message");

        final String formatted = target.getLastFormattedEvent();
        assertThat(formatted).contains("method=velocity.velocity_dep.ExtendedPatternFormatterTest.");
        assertThat(formatted).contains("formatsCallerMethodPatternThroughLogger");
        assertThat(formatted).endsWith(";message=formatter message");
    }

    @Test
    @Order(2)
    void formatsMethodPatternWhenContextMapDoesNotProvideMethod() {
        final ExtendedPatternFormatter formatter = new ExtendedPatternFormatter("method=%{method};message=%{message}");
        final ContextMap contextMap = new ContextMap();
        contextMap.set("other", "value");
        final LogEvent event = new LogEvent();
        event.setContextMap(contextMap);
        event.setMessage("context fallback message");

        final String formatted = formatter.format(event);

        assertThat(formatted).startsWith("method=");
        assertThat(formatted).endsWith(";message=context fallback message");
    }

    @Test
    @Order(3)
    void formatsUnknownMethodPatternWithoutLoggerCaller() {
        final ExtendedPatternFormatter formatter = new ExtendedPatternFormatter("method=%{method};message=%{message}");
        final LogEvent event = new LogEvent();
        event.setMessage("standalone formatter message");

        final String formatted = formatter.format(event);

        assertThat(formatted).startsWith("method=");
        assertThat(formatted).endsWith(";message=standalone formatter message");
    }

    private static final class CapturingTarget implements LogTarget {
        private final ExtendedPatternFormatter formatter;
        private String lastFormattedEvent;

        private CapturingTarget(final ExtendedPatternFormatter formatter) {
            this.formatter = formatter;
        }

        @Override
        public void processEvent(final LogEvent event) {
            lastFormattedEvent = formatter.format(event);
        }

        private String getLastFormattedEvent() {
            return lastFormattedEvent;
        }
    }
}
