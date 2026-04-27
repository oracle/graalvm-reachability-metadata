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
import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.helpers.PatternParser;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HelpersPatternParserTest {

    @Test
    void fallsBackToIso8601WhenDatePatternIsInvalid() {
        LoggingEvent event = new LoggingEvent(
                HelpersPatternParserTest.class.getName(),
                Logger.getLogger(HelpersPatternParserTest.class),
                123456789L,
                Level.INFO,
                "pattern-parser-fallback",
                null);

        String expected = new PatternLayout("%d{ISO8601} %m").format(event);
        String formattedByPatternLayout = new PatternLayout("%d{invalid[} %m").format(event);
        String formattedByHelperParser = formatWithHelperParser("%d{invalid[} %m", event);

        assertThat(formattedByPatternLayout).isEqualTo(expected);
        assertThat(formattedByHelperParser).isEqualTo(expected);
    }

    private static String formatWithHelperParser(String pattern, LoggingEvent event) {
        StringBuffer output = new StringBuffer();
        PatternConverter converter = new PatternParser(pattern).parse();
        while (converter != null) {
            converter.format(output, event);
            converter = converter.next;
        }
        return output.toString();
    }
}
