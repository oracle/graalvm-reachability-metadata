/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamConstants;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

public class CompactHashMapTest {
    private static final String CLASS_NAME =
            "org.apache.curator.shaded.com.google.common.collect.CompactHashMap";
    private static final long SERIAL_VERSION_UID = 5010194395228277517L;

    @Test
    void preservesEntriesThroughItsSerializationContract() throws Exception {
        Map<String, String> map = deserializeMap("letters", "abc", "numbers", "123");

        assertThat(map).containsExactly(entry("letters", "abc"), entry("numbers", "123"));
        map.put("symbols", "!@#");

        Map<String, String> restored = roundTrip(map);

        assertThat(restored)
                .containsExactly(
                        entry("letters", "abc"), entry("numbers", "123"), entry("symbols", "!@#"));
    }

    private static Map<String, String> deserializeMap(String... keysAndValues)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(serializedMap(keysAndValues)))) {
            Object value = input.readObject();
            assertThat(value).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) value;
            return map;
        }
    }

    private static byte[] serializedMap(String... keysAndValues) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeShort(ObjectStreamConstants.STREAM_MAGIC);
        output.writeShort(ObjectStreamConstants.STREAM_VERSION);
        output.writeByte(ObjectStreamConstants.TC_OBJECT);
        output.writeByte(ObjectStreamConstants.TC_CLASSDESC);
        output.writeUTF(CLASS_NAME);
        output.writeLong(SERIAL_VERSION_UID);
        output.writeByte(ObjectStreamConstants.SC_SERIALIZABLE | ObjectStreamConstants.SC_WRITE_METHOD);
        output.writeShort(0);
        output.writeByte(ObjectStreamConstants.TC_ENDBLOCKDATA);
        output.writeByte(ObjectStreamConstants.TC_NULL);
        output.writeByte(ObjectStreamConstants.TC_BLOCKDATA);
        output.writeByte(Integer.BYTES);
        output.writeInt(keysAndValues.length / 2);
        for (String keyOrValue : keysAndValues) {
            output.writeByte(ObjectStreamConstants.TC_STRING);
            output.writeUTF(keyOrValue);
        }
        output.writeByte(ObjectStreamConstants.TC_ENDBLOCKDATA);
        output.flush();
        return bytes.toByteArray();
    }

    private static Map<String, String> roundTrip(Map<String, String> map)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }

        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Object value = input.readObject();
            assertThat(value).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, String> restored = (Map<String, String>) value;
            return restored;
        }
    }
}
