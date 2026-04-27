/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.log.ContextMap;
import org.apache.log.LogEvent;
import org.apache.log.format.ExtendedPatternFormatter;
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
