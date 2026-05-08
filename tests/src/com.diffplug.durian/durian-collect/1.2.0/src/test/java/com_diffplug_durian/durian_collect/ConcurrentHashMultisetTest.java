/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import com.diffplug.common.collect.ConcurrentHashMultiset;

public class ConcurrentHashMultisetTest {
    @Test
    void serializesCountsAndRemainsMutableAfterDeserialization() throws Exception {
        ConcurrentHashMultiset<String> original = ConcurrentHashMultiset.create();
        original.add("alpha", 2);
        original.add("beta", 3);
        original.add("gamma", 1);
        original.removeExactly("gamma", 1);

        ConcurrentHashMultiset<String> copy = roundTrip(original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.count("alpha")).isEqualTo(2);
        assertThat(copy.count("beta")).isEqualTo(3);
        assertThat(copy.count("gamma")).isZero();
        assertThat(copy.elementSet()).containsExactlyInAnyOrder("alpha", "beta");

        assertThat(copy.add("alpha", 4)).isEqualTo(2);
        assertThat(copy.remove("beta", 2)).isEqualTo(3);
        assertThat(copy.setCount("delta", 5)).isZero();
        assertThat(copy.count("alpha")).isEqualTo(6);
        assertThat(copy.count("beta")).isEqualTo(1);
        assertThat(copy.count("delta")).isEqualTo(5);
    }

    private static ConcurrentHashMultiset<String> roundTrip(ConcurrentHashMultiset<String> original)
            throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
            output.flush();
            serialized = bytes.toByteArray();
        }

        Object copy;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            copy = input.readObject();
        }

        assertThat(copy).isInstanceOf(ConcurrentHashMultiset.class);
        @SuppressWarnings("unchecked")
        ConcurrentHashMultiset<String> typedCopy = (ConcurrentHashMultiset<String>) copy;
        return typedCopy;
    }
}
