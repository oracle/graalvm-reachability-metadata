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
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompactHashSetTest {
    private static final String CLASS_NAME =
            "org.apache.curator.shaded.com.google.common.collect.CompactHashSet";
    private static final long SERIAL_VERSION_UID = 2089243921681171393L;

    @Test
    void preservesUniqueElementsAndMutabilityThroughItsSerializationContract() throws Exception {
        Set<String> set = deserializeSet("alpha", "beta", "alpha");

        assertThat(set).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(set.add("gamma")).isTrue();
        assertThat(set.remove("beta")).isTrue();

        Set<String> restored = roundTrip(set);

        assertThat(restored).containsExactlyInAnyOrder("alpha", "gamma");
        assertThat(restored.add("delta")).isTrue();
        assertThat(restored).containsExactlyInAnyOrder("alpha", "gamma", "delta");
    }

    private static Set<String> deserializeSet(String... elements) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(serializedSet(elements)))) {
            Object value = input.readObject();
            assertThat(value).isInstanceOf(Set.class);
            @SuppressWarnings("unchecked")
            Set<String> set = (Set<String>) value;
            return set;
        }
    }

    private static byte[] serializedSet(String... elements) throws IOException {
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
        output.writeInt(elements.length);
        for (String element : elements) {
            output.writeByte(ObjectStreamConstants.TC_STRING);
            output.writeUTF(element);
        }
        output.writeByte(ObjectStreamConstants.TC_ENDBLOCKDATA);
        output.flush();
        return bytes.toByteArray();
    }

    private static Set<String> roundTrip(Set<String> set) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(set);
        }

        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Object value = input.readObject();
            assertThat(value).isInstanceOf(Set.class);
            @SuppressWarnings("unchecked")
            Set<String> restored = (Set<String>) value;
            return restored;
        }
    }
}
