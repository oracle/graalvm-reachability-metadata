/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static java.util.Comparator.reverseOrder;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class TreeMultisetTest {
    @Test
    void roundTripSerializesComparatorElementsAndCounts() throws Exception {
        TreeMultiset<String> multiset = TreeMultiset.create(reverseOrder());
        multiset.add("alpha", 3);
        multiset.add("gamma", 1);
        multiset.add("beta", 2);
        multiset.remove("alpha", 1);

        TreeMultiset<String> restored = roundTrip(multiset);

        assertThat(restored).isNotSameAs(multiset);
        assertThat(restored).isEqualTo(multiset);
        assertThat(restored.count("alpha")).isEqualTo(2);
        assertThat(restored.count("beta")).isEqualTo(2);
        assertThat(restored.count("gamma")).isEqualTo(1);
        assertThat(restored.elementSet()).containsExactly("gamma", "beta", "alpha");
        assertThat(restored).containsExactly("gamma", "beta", "beta", "alpha", "alpha");

        Multiset.Entry<String> firstEntry = restored.pollFirstEntry();
        assertThat(firstEntry.getElement()).isEqualTo("gamma");
        assertThat(firstEntry.getCount()).isEqualTo(1);
        restored.add("delta", 4);
        assertThat(restored.elementSet()).containsExactly("delta", "beta", "alpha");
        assertThat(restored.count("delta")).isEqualTo(4);
    }

    private static TreeMultiset<String> roundTrip(TreeMultiset<String> value)
            throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
            outputStream.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(TreeMultiset.class);
            @SuppressWarnings("unchecked")
            TreeMultiset<String> typedRestored = (TreeMultiset<String>) restored;
            return typedRestored;
        }
    }
}
