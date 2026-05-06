/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.helpers.MDCKeySetExtractor;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MDCKeySetExtractorTest {
    @Test
    void extractsPropertyKeysThroughLoggingEventAccessor() throws Exception {
        LoggingEvent event = loggingEventWithProperties();

        Set keySet = MDCKeySetExtractor.INSTANCE.getPropertyKeySet(event);

        assertThat(keySet).containsExactlyInAnyOrder("requestId", "tenant");
    }

    @Test
    void extractsPropertyKeysThroughLegacySerializationFallback() throws Exception {
        MDCKeySetExtractor extractor = MDCKeySetExtractor.INSTANCE;
        Field getKeySetField = MDCKeySetExtractor.class.getDeclaredField("getKeySetMethod");
        getKeySetField.setAccessible(true);
        Method originalGetKeySetMethod = (Method) getKeySetField.get(extractor);
        assertThat(originalGetKeySetMethod).isNotNull();

        getKeySetField.set(extractor, null);
        try {
            Set keySet = extractor.getPropertyKeySet(loggingEventWithProperties());

            assertThat(keySet).containsExactlyInAnyOrder("requestId", "tenant");
        } finally {
            getKeySetField.set(extractor, originalGetKeySetMethod);
        }
    }

    private static LoggingEvent loggingEventWithProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("requestId", "request-123");
        properties.put("tenant", "native-image-tests");
        return new LoggingEvent(
                MDCKeySetExtractorTest.class.getName(),
                null,
                System.currentTimeMillis(),
                Level.INFO,
                "message with mapped diagnostic context",
                "reload4j-test-thread",
                null,
                null,
                null,
                properties);
    }
}
