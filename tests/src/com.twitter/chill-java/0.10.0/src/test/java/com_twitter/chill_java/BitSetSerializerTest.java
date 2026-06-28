/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.chill_java;

import static org.assertj.core.api.Assertions.assertThat;

import com.twitter.chill.KryoInstantiator;
import com.twitter.chill.KryoPool;
import com.twitter.chill.java.PackageRegistrar;
import java.util.BitSet;
import org.junit.jupiter.api.Test;

public class BitSetSerializerTest {
    @Test
    void serializesBitSetThroughPackageRegistrar() {
        KryoInstantiator instantiator = new KryoInstantiator().withRegistrar(PackageRegistrar.all());
        KryoPool pool = KryoPool.withByteArrayOutputStream(2, instantiator);
        BitSet original = new BitSet();
        original.set(0);
        original.set(7);
        original.set(63);
        original.set(64);
        original.set(511);
        original.clear(7);

        assertThat(pool.hasRegistration(BitSet.class)).isTrue();

        byte[] serialized = pool.toBytesWithoutClass(original);
        BitSet roundTripped = pool.fromBytes(serialized, BitSet.class);

        assertThat(roundTripped).isEqualTo(original);
        assertThat(roundTripped.cardinality()).isEqualTo(4);
        assertThat(roundTripped.get(511)).isTrue();
    }

}
