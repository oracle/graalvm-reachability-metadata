/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package redis_clients.jedis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import redis.clients.jedis.StreamEntry;
import redis.clients.jedis.StreamEntryID;

public class StreamEntryTest {
    @Test
    void serializesAndDeserializesStreamEntryIdAndFields() throws Exception {
        StreamEntryID entryId = new StreamEntryID(1_628_784_000_000L, 7L);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("event", "order-created");
        fields.put("region", "eu-central");
        StreamEntry original = new StreamEntry(entryId, fields);

        StreamEntry restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getID()).isEqualTo(entryId);
        assertThat(restored.getFields()).isEqualTo(fields);
        assertThat(restored.toString()).isEqualTo("1628784000000-7 {event=order-created, region=eu-central}");
    }

    private static byte[] serialize(Serializable value) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            objectStream.writeObject(value);
        }
        return byteStream.toByteArray();
    }

    private static StreamEntry deserialize(byte[] value) throws Exception {
        try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(value))) {
            return (StreamEntry) objectStream.readObject();
        }
    }
}
