/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package logkit.logkit;

import org.apache.log.LogEvent;
import org.apache.log.format.ExtendedPatternFormatter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtendedPatternFormatterTest {
    @Test
    void methodPatternFallsBackToCallerIntrospectionWhenMethodContextIsAbsent() {
        ExtendedPatternFormatter formatter = new ExtendedPatternFormatter("method=%{method}");
        LogEvent event = new LogEvent();

        String formatted = formatter.format(event);

        assertThat(formatted).startsWith("method=");
        assertThat(formatted).isNotEqualTo("method=");
    }
}
