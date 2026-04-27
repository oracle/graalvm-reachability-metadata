/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HelpersPatternParserTest {

    @Test
    void fallsBackToIso8601WhenDatePatternIsInvalid() {
        PatternLayout layout = new PatternLayout("%d{'} %m");
        LoggingEvent event = new LoggingEvent(
                HelpersPatternParserTest.class.getName(),
                Logger.getLogger(HelpersPatternParserTest.class),
                Level.INFO,
                "pattern-parser-fallback",
                null);

        String formatted = layout.format(event);

        assertThat(formatted).endsWith(" pattern-parser-fallback");
        assertThat(formatted.substring(0, formatted.length() - " pattern-parser-fallback".length())).isNotBlank();
    }
}
