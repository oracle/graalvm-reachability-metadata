/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CompactHashMapTest {
    private static final String COMPACT_HASH_MAP_CLASS_NAME =
            "com.google.common.collect.CompactHashMap";
    private static final long COMPACT_HASH_MAP_SERIAL_VERSION_UID = -344891455263807780L;

    @Test
    void roundTripSerializesCompactHashMapKeysAndValues() throws Exception {
        Map<String, String> restored = readSerializedCompactHashMap();

        assertThat(restored).containsExactly(entry("alpha", "one"), entry("beta", "two"));

        Map<String, String> roundTripped = roundTrip(restored);

        assertThat(roundTripped).containsExactly(entry("alpha", "one"), entry("beta", "two"));
    }

    private static Map<String, String> readSerializedCompactHashMap()
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream inputBytes = new ByteArrayInputStream(serializedCompactHashMap());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, String> typedRestored = (Map<String, String>) restored;
            return typedRestored;
        }
    }

    private static Map<String, String> roundTrip(Map<String, String> value)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, String> typedRestored = (Map<String, String>) restored;
            return typedRestored;
        }
    }

    private static byte[] serializedCompactHashMap() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(bytes);
        stream.writeShort(0xaced); // stream magic
        stream.writeShort(5); // stream version
        stream.writeByte(0x73); // TC_OBJECT
        stream.writeByte(0x72); // TC_CLASSDESC
        stream.writeUTF(COMPACT_HASH_MAP_CLASS_NAME);
        stream.writeLong(COMPACT_HASH_MAP_SERIAL_VERSION_UID);
        stream.writeByte(0x03); // SC_WRITE_METHOD | SC_SERIALIZABLE
        stream.writeShort(0); // no serializable fields
        stream.writeByte(0x78); // TC_ENDBLOCKDATA
        stream.writeByte(0x70); // TC_NULL superclass descriptor

        stream.writeByte(0x77); // TC_BLOCKDATA
        stream.writeByte(4); // readObject consumes one int from this block
        stream.writeInt(2);
        writeString(stream, "alpha");
        writeString(stream, "one");
        writeString(stream, "beta");
        writeString(stream, "two");
        stream.writeByte(0x78); // TC_ENDBLOCKDATA for writeObject data
        return bytes.toByteArray();
    }

    private static void writeString(DataOutputStream stream, String value) throws IOException {
        stream.writeByte(0x74); // TC_STRING
        stream.writeUTF(value);
    }
}
