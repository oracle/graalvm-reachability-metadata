/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.lang.reflect.Field;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.helpers.MDCKeySetExtractor;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import static org.assertj.core.api.Assertions.assertThat;

public class MDCKeySetExtractorTest {

    @Test
    void extractsPropertyKeysUsingLoggingEventAccessor() throws Exception {
        LoggingEvent event = new LoggingEvent(
                MDCKeySetExtractorTest.class.getName(),
                Logger.getLogger("mdc-key-set-accessor"),
                Level.INFO,
                "accessor branch",
                null);
        event.setProperty("requestId", "123");
        event.setProperty("tenant", "acme");

        Set keySet = MDCKeySetExtractor.INSTANCE.getPropertyKeySet(event);

        assertThat(keySet).containsExactlyInAnyOrder("requestId", "tenant");
    }

    @Test
    void extractsPropertyKeysFromSerializedEventWhenAccessorLookupIsUnavailable() throws Exception {
        MDCKeySetExtractor extractor = createExtractorWithoutAccessorMethod();
        LoggingEvent event = new LoggingEvent(
                MDCKeySetExtractorTest.class.getName(),
                Logger.getLogger("mdc-key-set-fallback"),
                Level.INFO,
                "serialization branch",
                null);
        event.setProperty("sessionId", "abc");
        event.setProperty("traceId", "def");

        Set keySet = extractor.getPropertyKeySet(event);

        assertThat(keySet).containsExactlyInAnyOrder("sessionId", "traceId");
    }

    private static MDCKeySetExtractor createExtractorWithoutAccessorMethod() throws ReflectiveOperationException, InstantiationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        return (MDCKeySetExtractor) unsafe.allocateInstance(MDCKeySetExtractor.class);
    }
}
