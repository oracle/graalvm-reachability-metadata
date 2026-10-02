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
import java.io.Serializable;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompactHashMapTest {
    private static final String CLASS_NAME =
            "org.apache.curator.shaded.com.google.common.collect.CompactHashMap";
    private static final long SERIAL_VERSION_UID = 6735099499354660006L;

    @Test
    void serializedCompactMapPreservesEntriesAndRemainsMutable() throws Exception {
        Map<String, String> restored = deserializeMap(serializedMap());

        assertThat(restored).containsEntry("alpha", "one").containsEntry("beta", "two");

        restored.put("gamma", "three");
        Map<String, String> roundTripped = deserializeMap(serialize((Serializable) restored));

        assertThat(roundTripped)
                .containsEntry("alpha", "one")
                .containsEntry("beta", "two")
                .containsEntry("gamma", "three");
    }

    private static byte[] serializedMap() throws IOException {
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
        output.writeInt(2);
        writeString(output, "alpha");
        writeString(output, "one");
        writeString(output, "beta");
        writeString(output, "two");
        output.writeByte(ObjectStreamConstants.TC_ENDBLOCKDATA);
        output.flush();
        return bytes.toByteArray();
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        output.writeByte(ObjectStreamConstants.TC_STRING);
        output.writeUTF(value);
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Map<String, String> deserializeMap(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object restored = input.readObject();
            assertThat(restored).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, String> typedRestored = (Map<String, String>) restored;
            return typedRestored;
        }
    }
}
