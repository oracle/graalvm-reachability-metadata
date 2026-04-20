/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.encoder.EventObjectInputStream;
import ch.qos.logback.core.encoder.ObjectStreamEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

public class EventObjectInputStreamTest {

    @Test
    void readsSerializedEventsFromAnObjectStreamEncoderPayload() throws Exception {
        SerializablePayload firstEvent = new SerializablePayload("first-event");
        SerializablePayload secondEvent = new SerializablePayload("second-event");
        byte[] encodedEvents = encode(firstEvent, secondEvent);

        try (EventObjectInputStream<SerializablePayload> inputStream = newEventObjectInputStream(
                new ByteArrayInputStream(encodedEvents))) {
            SerializablePayload restoredFirstEvent = inputStream.readEvent();
            SerializablePayload restoredSecondEvent = inputStream.readEvent();
            SerializablePayload endOfStream = inputStream.readEvent();

            assertThat(restoredFirstEvent).isNotNull();
            assertThat(restoredFirstEvent.getValue()).isEqualTo(firstEvent.getValue());
            assertThat(restoredSecondEvent).isNotNull();
            assertThat(restoredSecondEvent.getValue()).isEqualTo(secondEvent.getValue());
            assertThat(endOfStream).isNull();
        }
    }

    private static byte[] encode(SerializablePayload... events) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectStreamEncoder<SerializablePayload> encoder = new ObjectStreamEncoder<>();
        encoder.init(outputStream);

        for (SerializablePayload event : events) {
            encoder.doEncode(event);
        }

        encoder.close();
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static EventObjectInputStream<SerializablePayload> newEventObjectInputStream(InputStream inputStream)
            throws Exception {
        Constructor<EventObjectInputStream> constructor = EventObjectInputStream.class.getDeclaredConstructor(
                InputStream.class);
        constructor.setAccessible(true);
        return constructor.newInstance(inputStream);
    }

    private static final class SerializablePayload implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String value;

        private SerializablePayload(String value) {
            this.value = value;
        }

        private String getValue() {
            return value;
        }
    }
}
