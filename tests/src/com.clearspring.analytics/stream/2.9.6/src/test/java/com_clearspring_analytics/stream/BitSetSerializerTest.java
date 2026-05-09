/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clearspring_analytics.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.BitSet;

import org.junit.jupiter.api.Test;

import com.clearspring.analytics.stream.membership.BitSetSerializer;

public class BitSetSerializerTest {
    @Test
    void roundTripsPopulatedBitSet() throws Exception {
        BitSet original = new BitSet(256);
        original.set(0);
        original.set(7);
        original.set(63);
        original.set(128);
        original.set(255);

        byte[] serialized;
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(byteStream)) {
            BitSetSerializer.serialize(original, output);
            output.flush();
            serialized = byteStream.toByteArray();
        }

        BitSet deserialized;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(serialized))) {
            deserialized = BitSetSerializer.deserialize(input);
        }

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.length()).isEqualTo(256);
        assertThat(deserialized.cardinality()).isEqualTo(5);
    }
}
