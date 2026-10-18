/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kotlin.UByte;
import kotlin.UInt;
import kotlin.ULong;
import kotlin.UShort;
import org.junit.jupiter.api.Test;

/// Kotlin unsigned numbers (UByte, UShort, UInt, ULong) as values and map keys, bound by the
/// Kotlin module's unsigned serializers and deserializers.
///
/// A Java telemetry collector ingests counter snapshots from Kotlin edge agents, which report
/// their counters with Kotlin's unsigned types so that values above the signed maximum stay
/// exact. The collector reads the snapshots, sorts and re-publishes them, and rejects a
/// snapshot whose counter does not fit its unsigned type.
class KotlinUnsignedNumbersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder().build());

    /// One counter snapshot an edge agent sends per reporting window.
    public static final class CounterSnapshot {
        public String agent;
        public UByte priority;
        public UShort port;
        public UInt retries;
        public ULong bytesSent;
        public List<UInt> latencyBuckets;
        public Map<UShort, ULong> bytesPerPort;
    }

    @Test
    void readsCountersAboveTheSignedRange() throws Exception {
        String json = """
                {
                  "agent": "edge-7",
                  "priority": 255,
                  "port": 65535,
                  "retries": 4294967295,
                  "bytesSent": 18446744073709551615,
                  "latencyBuckets": [3000000000, 12, 2147483648],
                  "bytesPerPort": {"443": 9223372036854775808, "65000": 7}
                }
                """;

        CounterSnapshot snapshot = MAPPER.readValue(json, CounterSnapshot.class);

        assertEquals("edge-7", snapshot.agent);
        assertEquals("255", snapshot.priority.toString());
        assertEquals("65535", snapshot.port.toString());
        assertEquals("4294967295", snapshot.retries.toString());
        assertEquals("18446744073709551615", snapshot.bytesSent.toString());

        List<UInt> sorted = new ArrayList<>(snapshot.latencyBuckets);
        sorted.sort(null);
        assertEquals(List.of("12", "2147483648", "3000000000"),
                sorted.stream().map(UInt::toString).toList());

        Map<String, String> perPort = new TreeMap<>();
        snapshot.bytesPerPort.forEach((port, bytes) -> perPort.put(port.toString(), bytes.toString()));
        assertEquals(Map.of("443", "9223372036854775808", "65000", "7"), perPort);
    }

    @Test
    void republishesSnapshotsWithExactUnsignedValues() throws Exception {
        String incoming = """
                {
                  "agent": "edge-3",
                  "priority": 200,
                  "port": 50000,
                  "retries": 3000000000,
                  "bytesSent": 10000000000000000000,
                  "latencyBuckets": [4294967295],
                  "bytesPerPort": {"8443": 18446744073709551615}
                }
                """;
        CounterSnapshot snapshot = MAPPER.readValue(incoming, CounterSnapshot.class);

        String published = MAPPER.writeValueAsString(snapshot);

        JsonNode tree = MAPPER.readTree(published);
        assertEquals(200, tree.get("priority").intValue());
        assertEquals(50000, tree.get("port").intValue());
        assertEquals(3000000000L, tree.get("retries").longValue());
        assertEquals("10000000000000000000", tree.get("bytesSent").bigIntegerValue().toString());
        assertEquals(4294967295L, tree.get("latencyBuckets").get(0).longValue());
        assertEquals("18446744073709551615",
                tree.get("bytesPerPort").get("8443").bigIntegerValue().toString());

        CounterSnapshot reread = MAPPER.readValue(published, CounterSnapshot.class);
        assertEquals(snapshot.priority, reread.priority);
        assertEquals(snapshot.port, reread.port);
        assertEquals(snapshot.retries, reread.retries);
        assertEquals(snapshot.bytesSent, reread.bytesSent);
        assertEquals(snapshot.latencyBuckets, reread.latencyBuckets);
        assertEquals(snapshot.bytesPerPort, reread.bytesPerPort);
    }

    @Test
    void rejectsCountersOutsideTheirUnsignedRange() {
        JsonMappingException negativeRetries = assertThrows(JsonMappingException.class,
                () -> MAPPER.readValue("{\"retries\": -1}", CounterSnapshot.class));
        assertInstanceOf(InputCoercionException.class, negativeRetries.getCause());
        assertTrue(negativeRetries.getMessage().contains("UInt"), negativeRetries.getMessage());

        JsonMappingException wideBytes = assertThrows(JsonMappingException.class,
                () -> MAPPER.readValue("{\"bytesSent\": 18446744073709551616}", CounterSnapshot.class));
        assertInstanceOf(InputCoercionException.class, wideBytes.getCause());
        assertTrue(wideBytes.getMessage().contains("ULong"), wideBytes.getMessage());

        JsonMappingException widePriority = assertThrows(JsonMappingException.class,
                () -> MAPPER.readValue("{\"priority\": 256}", CounterSnapshot.class));
        assertInstanceOf(InputCoercionException.class, widePriority.getCause());
        assertTrue(widePriority.getMessage().contains("UByte"), widePriority.getMessage());
    }
}
