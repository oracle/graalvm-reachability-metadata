/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

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

    @Test
    void fallsBackToIso8601WhenDatePatternIsInvalidInFreshClassLoader() throws Exception {
        try (URLClassLoader isolatedLoader = new URLClassLoader(new URL[] { codeSourceUrl(PatternLayout.class) }, ClassLoader.getPlatformClassLoader())) {
            Thread thread = Thread.currentThread();
            ClassLoader previousContextClassLoader = thread.getContextClassLoader();
            thread.setContextClassLoader(isolatedLoader);
            try {
                Class<?> patternLayoutClass = Class.forName("org.apache.log4j.PatternLayout", true, isolatedLoader);
                Class<?> categoryClass = Class.forName("org.apache.log4j.Category", true, isolatedLoader);
                Class<?> priorityClass = Class.forName("org.apache.log4j.Priority", true, isolatedLoader);
                Class<?> loggerClass = Class.forName("org.apache.log4j.Logger", true, isolatedLoader);
                Class<?> levelClass = Class.forName("org.apache.log4j.Level", true, isolatedLoader);
                Class<?> loggingEventClass = Class.forName("org.apache.log4j.spi.LoggingEvent", true, isolatedLoader);

                Object logger = loggerClass.getMethod("getLogger", String.class).invoke(null, HelpersPatternParserTest.class.getName());
                Object infoLevel = levelClass.getField("INFO").get(null);
                Constructor<?> loggingEventConstructor = loggingEventClass.getConstructor(
                        String.class,
                        categoryClass,
                        long.class,
                        priorityClass,
                        Object.class,
                        Throwable.class);
                Object event = loggingEventConstructor.newInstance(
                        HelpersPatternParserTest.class.getName(),
                        logger,
                        123456789L,
                        infoLevel,
                        "pattern-parser-fallback",
                        null);

                String expected = formatInIsolatedLoader(patternLayoutClass, loggingEventClass, event, "%d{ISO8601} %m");
                String formatted = formatInIsolatedLoader(patternLayoutClass, loggingEventClass, event, "%d{invalid[} %m");

                assertThat(formatted).isEqualTo(expected);
            } finally {
                thread.setContextClassLoader(previousContextClassLoader);
            }
        }
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

    private static String formatInIsolatedLoader(Class<?> patternLayoutClass, Class<?> loggingEventClass, Object event, String pattern) throws Exception {
        Object layout = patternLayoutClass.getConstructor(String.class).newInstance(pattern);
        Method formatMethod = patternLayoutClass.getMethod("format", loggingEventClass);
        return (String) formatMethod.invoke(layout, event);
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }
}
