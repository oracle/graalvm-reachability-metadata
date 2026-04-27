/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.helpers.MDCKeySetExtractor;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

public class MDCKeySetExtractorTest {
    private static final Logger LOGGER = Logger.getLogger(MDCKeySetExtractorTest.class);
    private static final String LOGGER_FQCN = MDCKeySetExtractorTest.class.getName();

    @Test
    void extractsMappedDiagnosticContextKeysFromLoggingEvent() throws Exception {
        MDC.put("requestId", "request-42");
        MDC.put("tenant", "metadata-forge");
        try {
            LoggingEvent event = new LoggingEvent(LOGGER_FQCN, LOGGER, Level.INFO,
                    "message with mapped diagnostic context", null);

            Set propertyKeys = MDCKeySetExtractor.INSTANCE.getPropertyKeySet(event);

            assertThat(propertyKeys).containsExactlyInAnyOrder("requestId", "tenant");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void extractsMappedDiagnosticContextKeysThroughLegacySerializationFallback() throws Exception {
        Field keySetMethodField = MDCKeySetExtractor.class.getDeclaredField("getKeySetMethod");
        keySetMethodField.setAccessible(true);
        Method originalKeySetMethod = (Method) keySetMethodField.get(MDCKeySetExtractor.INSTANCE);
        keySetMethodField.set(MDCKeySetExtractor.INSTANCE, null);
        MDC.put("operation", "serialize");
        MDC.put("component", "mdc-extractor");
        try {
            LoggingEvent event = new LoggingEvent(LOGGER_FQCN, LOGGER, Level.WARN,
                    "message for legacy mapped diagnostic context extraction", null);

            Set propertyKeys = MDCKeySetExtractor.INSTANCE.getPropertyKeySet(event);

            assertThat(propertyKeys).containsExactlyInAnyOrder("operation", "component");
        } finally {
            MDC.clear();
            keySetMethodField.set(MDCKeySetExtractor.INSTANCE, originalKeySetMethod);
        }
    }
}
