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
import com.twitter.chill.java.PackageRegistrar;
import java.util.Comparator;
import java.util.PriorityQueue;
import org.junit.jupiter.api.Test;

public class PriorityQueueSerializerTest {
    @Test
    void roundTripsPriorityQueueWithComparatorRegisteredByPackageRegistrar() {
        Kryo kryo = new Kryo();
        PackageRegistrar.all().apply(kryo);
        PriorityQueue<String> original = new PriorityQueue<>(new LengthDescendingComparator());
        original.add("a");
        original.add("bbb");
        original.add("cc");

        Output output = new Output(128, 4096);
        kryo.writeObject(output, original);
        output.flush();

        Input input = new Input(output.toBytes());
        PriorityQueue<?> copy = kryo.readObject(input, PriorityQueue.class);

        assertThat(copy).hasSize(original.size());
        assertThat(copy.poll()).isEqualTo("bbb");
        assertThat(copy.poll()).isEqualTo("cc");
        assertThat(copy.poll()).isEqualTo("a");
    }

    public static final class LengthDescendingComparator implements Comparator<String> {
        @Override
        public int compare(String left, String right) {
            int lengthComparison = Integer.compare(right.length(), left.length());
            if (lengthComparison != 0) {
                return lengthComparison;
            }
            return left.compareTo(right);
        }
    }
}
