/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package logkit.logkit;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.log.ContextMap;
import org.apache.log.Hierarchy;
import org.apache.log.LogEvent;
import org.apache.log.LogTarget;
import org.apache.log.Logger;
import org.apache.log.format.ExtendedPatternFormatter;
import org.junit.jupiter.api.Test;

public class ExtendedPatternFormatterTest {
    @Test
    void formatsMethodAndThreadPatternsThroughLogger() {
        ExtendedPatternFormatter formatter = new ExtendedPatternFormatter(
                "method=%{method};thread=%{thread};message=%{message}");
        CapturingTarget target = new CapturingTarget(formatter);
        Hierarchy hierarchy = new Hierarchy();
        hierarchy.setDefaultLogTarget(target);
        Logger logger = hierarchy.getLoggerFor("formatter.category");

        logger.info("first formatter message");
        String firstFormatted = target.getLastFormattedEvent();
        logger.info("second formatter message");
        String secondFormatted = target.getLastFormattedEvent();

        assertThat(firstFormatted).contains("method=logkit.logkit.ExtendedPatternFormatterTest.");
        assertThat(firstFormatted).contains("formatsMethodAndThreadPatternsThroughLogger");
        assertThat(firstFormatted).contains(";thread=" + Thread.currentThread().getName() + ";");
        assertThat(firstFormatted).endsWith("message=first formatter message");
        assertThat(secondFormatted).contains("method=logkit.logkit.ExtendedPatternFormatterTest.");
        assertThat(secondFormatted).endsWith("message=second formatter message");
    }

    @Test
    void formatsExtendedPatternsFromLogEventAndContextMap() {
        ExtendedPatternFormatter formatter = new ExtendedPatternFormatter(
                "method=%{method};thread=%{thread};message=%{message}");
        LogEvent event = new LogEvent();
        event.setMessage("formatter message");

        String formatted = formatter.format(event);

        assertThat(formatted).startsWith("method=");
        assertThat(formatted).contains(";thread=" + Thread.currentThread().getName() + ";");
        assertThat(formatted).endsWith("message=formatter message");

        ContextMap contextMap = new ContextMap();
        contextMap.set("method", "contextMethod");
        contextMap.set("thread", "contextThread");

        LogEvent contextEvent = new LogEvent();
        contextEvent.setContextMap(contextMap);
        contextEvent.setMessage("context message");

        ExtendedPatternFormatter contextFormatter = new ExtendedPatternFormatter(
                "%{method}|%{thread}|%{message}");

        String contextFormatted = contextFormatter.format(contextEvent);

        assertThat(contextFormatted).isEqualTo("contextMethod|contextThread|context message");
    }

    private static final class CapturingTarget implements LogTarget {
        private final ExtendedPatternFormatter formatter;
        private String lastFormattedEvent;

        private CapturingTarget(ExtendedPatternFormatter formatter) {
            this.formatter = formatter;
        }

        @Override
        public void processEvent(LogEvent event) {
            lastFormattedEvent = formatter.format(event);
        }

        private String getLastFormattedEvent() {
            return lastFormattedEvent;
        }
    }
}
