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
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class CompactHashSetTest {
    private static final String COMPACT_HASH_SET_CLASS_NAME =
            "org.apache.hadoop.thirdparty.com.google.common.collect.CompactHashSet";
    private static final long COMPACT_HASH_SET_SERIAL_VERSION_UID = -7313357044237808198L;

    @Test
    void roundTripsSerializedCompactHashSetElements() throws Exception {
        Object compactHashSet = deserializeCompactHashSet("alpha", "beta", "gamma");

        assertThat(compactHashSet).isInstanceOf(Set.class);
        Set<?> set = (Set<?>) compactHashSet;
        assertThat(set).hasSize(3);
        assertThat(set.containsAll(List.of("alpha", "beta", "gamma"))).isTrue();

        Object restored = roundTrip((Serializable) compactHashSet);

        assertThat(restored).isInstanceOf(Set.class);
        Set<?> restoredSet = (Set<?>) restored;
        assertThat(restoredSet).hasSize(3);
        assertThat(restoredSet.containsAll(List.of("alpha", "beta", "gamma"))).isTrue();
    }

    private static Object deserializeCompactHashSet(String... elements) throws IOException, ClassNotFoundException {
        ByteArrayInputStream inputBytes = new ByteArrayInputStream(serializedCompactHashSet(elements));
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            return inputStream.readObject();
        }
    }

    private static byte[] serializedCompactHashSet(String... elements) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeShort(ObjectStreamConstants.STREAM_MAGIC);
        output.writeShort(ObjectStreamConstants.STREAM_VERSION);
        output.writeByte(ObjectStreamConstants.TC_OBJECT);
        writeCompactHashSetClassDescriptor(output);
        writeCompactHashSetData(output, elements);
        output.flush();
        return bytes.toByteArray();
    }

    private static void writeCompactHashSetClassDescriptor(DataOutputStream output) throws IOException {
        output.writeByte(ObjectStreamConstants.TC_CLASSDESC);
        output.writeUTF(COMPACT_HASH_SET_CLASS_NAME);
        output.writeLong(COMPACT_HASH_SET_SERIAL_VERSION_UID);
        output.writeByte(ObjectStreamConstants.SC_SERIALIZABLE | ObjectStreamConstants.SC_WRITE_METHOD);
        output.writeShort(0);
        output.writeByte(ObjectStreamConstants.TC_ENDBLOCKDATA);
        output.writeByte(ObjectStreamConstants.TC_NULL);
    }

    private static void writeCompactHashSetData(DataOutputStream output, String... elements) throws IOException {
        output.writeByte(ObjectStreamConstants.TC_BLOCKDATA);
        output.writeByte(Integer.BYTES);
        output.writeInt(elements.length);
        for (String element : elements) {
            output.writeByte(ObjectStreamConstants.TC_STRING);
            output.writeUTF(element);
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
