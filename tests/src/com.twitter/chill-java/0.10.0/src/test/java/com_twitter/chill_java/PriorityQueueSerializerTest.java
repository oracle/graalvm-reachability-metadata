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
import java.util.PriorityQueue;
import org.junit.jupiter.api.Test;

public class PriorityQueueSerializerTest {
    @Test
    void serializesPriorityQueueThroughPackageRegistrar() {
        KryoInstantiator instantiator = new KryoInstantiator().withRegistrar(PackageRegistrar.all());
        KryoPool pool = KryoPool.withByteArrayOutputStream(2, instantiator);
        PriorityQueue<Integer> original = new PriorityQueue<Integer>();
        original.add(30);
        original.add(10);
        original.add(20);

        assertThat(pool.hasRegistration(PriorityQueue.class)).isTrue();

        byte[] serialized = pool.toBytesWithoutClass(original);
        PriorityQueue<?> roundTripped = pool.fromBytes(serialized, PriorityQueue.class);

        assertThat(roundTripped).hasSize(3);
        assertThat(roundTripped.poll()).isEqualTo(10);
        assertThat(roundTripped.poll()).isEqualTo(20);
        assertThat(roundTripped.poll()).isEqualTo(30);
    }

}
