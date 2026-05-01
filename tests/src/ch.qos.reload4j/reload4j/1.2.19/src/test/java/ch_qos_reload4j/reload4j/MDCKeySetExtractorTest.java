/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.helpers.MDCKeySetExtractor;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import sun.reflect.ReflectionFactory;

public class MDCKeySetExtractorTest {
    private static final Logger LOGGER = Logger.getLogger(MDCKeySetExtractorTest.class);

    @Test
    void returnsPropertyKeysThroughLoggingEventAccessor() throws Exception {
        LoggingEvent event = loggingEventWithProperty("requestId", "request-123");

        Set<?> propertyKeys = MDCKeySetExtractor.INSTANCE.getPropertyKeySet(event);

        assertThat(propertyKeys.toArray()).containsExactly("requestId");
    }

    @Test
    void returnsPropertyKeysThroughLegacySerializationFallback() throws Exception {
        LoggingEvent event = loggingEventWithProperty("tenant", "north");
        MDCKeySetExtractor legacyExtractor = extractorWithoutDiscoveredAccessor();

        Set<?> propertyKeys = legacyExtractor.getPropertyKeySet(event);

        assertThat(propertyKeys.toArray()).containsExactly("tenant");
    }

    private static LoggingEvent loggingEventWithProperty(String name, String value) {
        LoggingEvent event = new LoggingEvent(MDCKeySetExtractorTest.class.getName(), LOGGER, Level.INFO,
                "message with MDC property", null);
        event.setProperty(name, value);
        return event;
    }

    private static MDCKeySetExtractor extractorWithoutDiscoveredAccessor() throws Exception {
        // Match the state used with log4j versions that predate LoggingEvent#getPropertyKeySet.
        Constructor<Object> objectConstructor = Object.class.getDeclaredConstructor();
        ReflectionFactory reflectionFactory = ReflectionFactory.getReflectionFactory();
        Constructor<?> extractorConstructor = reflectionFactory.newConstructorForSerialization(
                MDCKeySetExtractor.class, objectConstructor);
        extractorConstructor.setAccessible(true);
        return (MDCKeySetExtractor) extractorConstructor.newInstance();
    }
}
