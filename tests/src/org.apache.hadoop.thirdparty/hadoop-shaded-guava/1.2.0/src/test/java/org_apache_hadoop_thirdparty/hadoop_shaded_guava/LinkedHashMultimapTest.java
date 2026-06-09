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

import org.apache.hadoop.thirdparty.com.google.common.collect.LinkedHashMultimap;
import org.junit.jupiter.api.Test;

public class LinkedHashMultimapTest {
    @Test
    void roundTripsLinkedHashMultimapUsingJavaSerialization() throws Exception {
        LinkedHashMultimap<String, String> original = LinkedHashMultimap.create();
        original.put("colors", "red");
        original.put("colors", "blue");
        original.put("shapes", "circle");
        original.put("colors", "green");

        @SuppressWarnings("unchecked")
        LinkedHashMultimap<String, String> restored =
                (LinkedHashMultimap<String, String>) roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.entries()).containsExactlyElementsOf(original.entries());
        assertThat(restored.get("colors")).containsExactly("red", "blue", "green");
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
