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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import redis.clients.jedis.BuilderFactory;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.resps.StreamPendingEntry;

public class StreamPendingEntryTest {
    @Test
    void buildsPendingEntryStateFromRedisResponse() {
        StreamEntryID entryId = new StreamEntryID(1_628_784_000_000L, 9L);
        StreamPendingEntry directEntry = new StreamPendingEntry(entryId, "consumer-a", 125L, 3L);

        List<StreamPendingEntry> builtEntries = BuilderFactory.STREAM_PENDING_ENTRY_LIST.build(Arrays.asList(
                Arrays.asList(bytes("1628784000000-9"), bytes("consumer-a"), 125L, 3L)));

        assertThat(directEntry.getID()).isEqualTo(entryId);
        assertThat(directEntry.getConsumerName()).isEqualTo("consumer-a");
        assertThat(directEntry.getIdleTime()).isEqualTo(125L);
        assertThat(directEntry.getDeliveredTimes()).isEqualTo(3L);
        assertThat(directEntry.toString()).isEqualTo("1628784000000-9 consumer-a idle:125 times:3");
        assertThat(builtEntries).hasSize(1);
        assertThat(builtEntries.get(0).getID()).isEqualTo(entryId);
        assertThat(builtEntries.get(0).getConsumerName()).isEqualTo("consumer-a");
        assertThat(builtEntries.get(0).getIdleTime()).isEqualTo(125L);
        assertThat(builtEntries.get(0).getDeliveredTimes()).isEqualTo(3L);
    }

    @Test
    void serializesAndDeserializesPendingEntryState() throws Exception {
        StreamEntryID entryId = new StreamEntryID(1_628_784_000_001L, 10L);
        StreamPendingEntry original = new StreamPendingEntry(entryId, "consumer-b", 250L, 4L);

        StreamPendingEntry restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getID()).isEqualTo(entryId);
        assertThat(restored.getConsumerName()).isEqualTo("consumer-b");
        assertThat(restored.getIdleTime()).isEqualTo(250L);
        assertThat(restored.getDeliveredTimes()).isEqualTo(4L);
        assertThat(restored.toString()).isEqualTo("1628784000001-10 consumer-b idle:250 times:4");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
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
