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
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompactHashSetTest {
    private static final String CLASS_NAME =
            "org.apache.curator.shaded.com.google.common.collect.CompactHashSet";
    private static final long SERIAL_VERSION_UID = -3289848359041770488L;

    @Test
    void serializedCompactSetPreservesElementsAndRemainsMutable() throws Exception {
        Set<String> restored = deserializeSet(serializedSet());

        assertThat(restored).containsExactlyInAnyOrder("alpha", "beta", "gamma");

        assertThat(restored.add("delta")).isTrue();
        Set<String> roundTripped = deserializeSet(serialize((Serializable) restored));

        assertThat(roundTripped).containsExactlyInAnyOrder("alpha", "beta", "gamma", "delta");
    }

    private static byte[] serializedSet() throws IOException {
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
        output.writeInt(3);
        writeString(output, "alpha");
        writeString(output, "beta");
        writeString(output, "gamma");
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

    private static Set<String> deserializeSet(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object restored = input.readObject();
            assertThat(restored).isInstanceOf(Set.class);
            @SuppressWarnings("unchecked")
            Set<String> typedRestored = (Set<String>) restored;
            return typedRestored;
        }
    }
}
