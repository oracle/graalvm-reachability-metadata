/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.io.ByteArrayOutputStream;

import org.apache.log.ContextMap;
import org.apache.log.Hierarchy;
import org.apache.log.LogEvent;
import org.apache.log.Logger;
import org.apache.log.Priority;
import org.apache.log.format.ExtendedPatternFormatter;
import org.apache.log.output.io.StreamTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtendedPatternFormatterTest {
    @Test
    void formatsMethodFromCallerStackWhenContextDoesNotProvideIt() {
        ExtendedPatternFormatter formatter = new ExtendedPatternFormatter("caller=%{method}");
        LogEvent event = new LogEvent();

        String formatted = formatter.format(event);

        assertThat(formatted).startsWith("caller=");
    }

    @Test
    void logsMethodFromLoggerCallStackWhenContextDoesNotProvideIt() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StreamTarget target = new StreamTarget(output, new ExtendedPatternFormatter("%{method}"));
        Hierarchy hierarchy = new Hierarchy();
        hierarchy.setDefaultLogTarget(target);
        hierarchy.setDefaultPriority(Priority.DEBUG);
        Logger logger = hierarchy.getLoggerFor("coverage.extendedPatternFormatter");

        logger.info("message");

        assertThat(output.toString()).isNotEmpty();
    }

    @Test
    void formatsExtendedFieldsFromContextWhenAvailable() {
        ExtendedPatternFormatter formatter = new ExtendedPatternFormatter("%{method}|%{thread}");
        ContextMap contextMap = new ContextMap();
        contextMap.set("method", "explicitMethod");
        contextMap.set("thread", "explicitThread");
        LogEvent event = new LogEvent();
        event.setContextMap(contextMap);

        String formatted = formatter.format(event);

        assertThat(formatted).isEqualTo("explicitMethod|explicitThread");
    }
}
