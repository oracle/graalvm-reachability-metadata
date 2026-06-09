/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamConstants;
import java.io.Serializable;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class CompactHashMapTest {
    private static final String COMPACT_HASH_MAP_CLASS_NAME =
            "org.apache.hadoop.thirdparty.com.google.common.collect.CompactHashMap";
    private static final long COMPACT_HASH_MAP_SERIAL_VERSION_UID = 6914122613132586155L;

    @Test
    void roundTripsSerializedCompactHashMapEntries() throws Exception {
        Object compactHashMap = deserializeCompactHashMap("alpha", "first", "beta", "second");

        assertThat(compactHashMap).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) compactHashMap;
        assertThat(map).hasSize(2);
        assertThat(map.get("alpha")).isEqualTo("first");
        assertThat(map.get("beta")).isEqualTo("second");

        Object restored = roundTrip((Serializable) compactHashMap);

        assertThat(restored).isInstanceOf(Map.class);
        Map<?, ?> restoredMap = (Map<?, ?>) restored;
        assertThat(restoredMap).hasSize(2);
        assertThat(restoredMap.get("alpha")).isEqualTo("first");
        assertThat(restoredMap.get("beta")).isEqualTo("second");
    }

    private static Object deserializeCompactHashMap(String... keysAndValues)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream inputBytes = new ByteArrayInputStream(serializedCompactHashMap(keysAndValues));
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            return inputStream.readObject();
        }
    }

    private static byte[] serializedCompactHashMap(String... keysAndValues) throws IOException {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Expected alternating keys and values");
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeShort(ObjectStreamConstants.STREAM_MAGIC);
        output.writeShort(ObjectStreamConstants.STREAM_VERSION);
        output.writeByte(ObjectStreamConstants.TC_OBJECT);
        writeCompactHashMapClassDescriptor(output);
        writeCompactHashMapData(output, keysAndValues);
        output.flush();
        return bytes.toByteArray();
    }

    private static void writeCompactHashMapClassDescriptor(DataOutputStream output) throws IOException {
        output.writeByte(ObjectStreamConstants.TC_CLASSDESC);
        output.writeUTF(COMPACT_HASH_MAP_CLASS_NAME);
        output.writeLong(COMPACT_HASH_MAP_SERIAL_VERSION_UID);
        output.writeByte(ObjectStreamConstants.SC_SERIALIZABLE | ObjectStreamConstants.SC_WRITE_METHOD);
        output.writeShort(0);
        output.writeByte(ObjectStreamConstants.TC_ENDBLOCKDATA);
        output.writeByte(ObjectStreamConstants.TC_NULL);
    }

    private static void writeCompactHashMapData(DataOutputStream output, String... keysAndValues) throws IOException {
        output.writeByte(ObjectStreamConstants.TC_BLOCKDATA);
        output.writeByte(Integer.BYTES);
        output.writeInt(keysAndValues.length / 2);
        for (String keyOrValue : keysAndValues) {
            output.writeByte(ObjectStreamConstants.TC_STRING);
            output.writeUTF(keyOrValue);
        }
        output.writeByte(ObjectStreamConstants.TC_ENDBLOCKDATA);
    }

    private static Object roundTrip(Serializable value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            return inputStream.readObject();
        }
    }
}
