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

import org.junit.jupiter.api.Test;

import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.StreamPendingEntry;

public class StreamPendingEntryTest {
    @Test
    void serializesAndDeserializesPendingEntryState() throws Exception {
        StreamEntryID entryId = new StreamEntryID(1_628_784_000_000L, 9L);
        StreamPendingEntry original = new StreamPendingEntry(entryId, "consumer-a", 125L, 3L);

        StreamPendingEntry restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getID()).isEqualTo(entryId);
        assertThat(restored.getConsumerName()).isEqualTo("consumer-a");
        assertThat(restored.getIdleTime()).isEqualTo(125L);
        assertThat(restored.getDeliveredTimes()).isEqualTo(3L);
        assertThat(restored.toString()).isEqualTo("1628784000000-9 consumer-a idle:125 times:3");
    }

    private static byte[] serialize(Serializable value) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            objectStream.writeObject(value);
        }
        return byteStream.toByteArray();
    }

    private static StreamPendingEntry deserialize(byte[] value) throws Exception {
        try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(value))) {
            return (StreamPendingEntry) objectStream.readObject();
        }
    }
}
