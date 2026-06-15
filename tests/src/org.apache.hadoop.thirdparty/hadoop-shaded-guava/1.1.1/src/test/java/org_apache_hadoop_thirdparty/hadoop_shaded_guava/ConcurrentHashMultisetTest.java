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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.hadoop.thirdparty.com.google.common.collect.ConcurrentHashMultiset;
import org.junit.jupiter.api.Test;

public class ConcurrentHashMultisetTest {
    @Test
    void roundTripsConcurrentHashMultisetUsingJavaSerialization() throws Exception {
        ConcurrentHashMultiset<String> original = ConcurrentHashMultiset.create();
        original.add("apple", 3);
        original.add("pear", 2);
        original.add("orange");
        original.remove("pear");

        @SuppressWarnings("unchecked")
        ConcurrentHashMultiset<String> restored =
                (ConcurrentHashMultiset<String>) roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.count("apple")).isEqualTo(3);
        assertThat(restored.count("pear")).isEqualTo(1);
        assertThat(restored.count("orange")).isEqualTo(1);
    }

    private static Object roundTrip(Object value) throws IOException, ClassNotFoundException {
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
