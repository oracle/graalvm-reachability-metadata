/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.chill_java;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.twitter.chill.java.BitSetSerializer;
import java.util.BitSet;
import org.junit.jupiter.api.Test;

public class BitSetSerializerTest {
    @Test
    void roundTripsBitSetWithRegisteredSerializer() {
        Kryo kryo = new Kryo();
        BitSetSerializer.registrar().apply(kryo);
        BitSet original = sparseBitSetWithUnusedCapacity();

        Output output = new Output(128, 4096);
        kryo.writeObject(output, original);
        output.flush();

        Input input = new Input(output.toBytes());
        BitSet copy = kryo.readObject(input, BitSet.class);

        assertThat(copy).isEqualTo(original);
        assertThat(copy.get(0)).isTrue();
        assertThat(copy.get(63)).isTrue();
        assertThat(copy.get(70)).isTrue();
        assertThat(copy.length()).isEqualTo(71);
    }

    private static BitSet sparseBitSetWithUnusedCapacity() {
        BitSet bitSet = new BitSet(256);
        bitSet.set(190);
        bitSet.clear(190);
        bitSet.set(0);
        bitSet.set(63);
        bitSet.set(70);
        return bitSet;
    }
}
