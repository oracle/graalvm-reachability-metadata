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
    void preservesPendingEntryStateAndIdOrdering() {
        StreamEntryID entryId = new StreamEntryID(1_628_784_000_001L, 10L);
        StreamEntryID parsedEntryId = new StreamEntryID("1628784000001-10");
        StreamEntryID binaryEntryId = new StreamEntryID(bytes("1628784000001-10"));

        StreamPendingEntry entry = new StreamPendingEntry(entryId, "consumer-b", 250L, 4L);

        assertThat(entry.getID()).isEqualTo(parsedEntryId);
        assertThat(entry.getID()).isEqualTo(binaryEntryId);
        assertThat(entry.getID().compareTo(new StreamEntryID(1_628_784_000_001L, 9L))).isPositive();
        assertThat(entry.getID().getTime()).isEqualTo(1_628_784_000_001L);
        assertThat(entry.getID().getSequence()).isEqualTo(10L);
        assertThat(entry.getConsumerName()).isEqualTo("consumer-b");
        assertThat(entry.getIdleTime()).isEqualTo(250L);
        assertThat(entry.getDeliveredTimes()).isEqualTo(4L);
        assertThat(entry.toString()).isEqualTo("1628784000001-10 consumer-b idle:250 times:4");
    }

    @Test
    void serializesAndDeserializesPendingEntryState() throws Exception {
        StreamEntryID entryId = new StreamEntryID(1_628_784_000_002L, 11L);
        StreamPendingEntry entry = new StreamPendingEntry(entryId, "consumer-c", 375L, 5L);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(entry);
        }

        StreamPendingEntry deserializedEntry;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            deserializedEntry = (StreamPendingEntry) input.readObject();
        }

        assertThat(deserializedEntry).isNotSameAs(entry);
        assertThat(deserializedEntry.getID()).isEqualTo(entryId);
        assertThat(deserializedEntry.getID()).isNotSameAs(entryId);
        assertThat(deserializedEntry.getConsumerName()).isEqualTo("consumer-c");
        assertThat(deserializedEntry.getIdleTime()).isEqualTo(375L);
        assertThat(deserializedEntry.getDeliveredTimes()).isEqualTo(5L);
        assertThat(deserializedEntry.toString()).isEqualTo("1628784000002-11 consumer-c idle:375 times:5");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
