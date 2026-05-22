/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import org.codehaus.plexus.util.FastMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class FastMapTest {
    @Test
    void serializesEntriesAndCopiesThemThroughPublicApi() throws Exception {
        FastMap original = new FastMap(4);
        original.put("alpha", "one");
        original.put("beta", Integer.valueOf(2));
        original.put("gamma", "three");

        byte[] serialized = serialize(original);
        FastMap deserialized = deserialize(serialized);
        FastMap copy = (FastMap) original.clone();

        assertThat(serialized).isNotEmpty();
        assertThat(deserialized).isNotSameAs(original);
        assertThat(deserialized.capacity()).isEqualTo(original.capacity());
        assertThat(deserialized).hasSize(3);
        assertThat(deserialized.get("alpha")).isEqualTo("one");
        assertThat(deserialized.get("beta")).isEqualTo(Integer.valueOf(2));
        assertThat(deserialized.get("gamma")).isEqualTo("three");
        assertThat(deserialized.entrySet())
                .extracting(entry -> ((Map.Entry) entry).getKey())
                .containsExactly("alpha", "beta", "gamma");
        assertThat(copy).isNotSameAs(original);
        assertThat(copy.capacity()).isEqualTo(original.capacity());
        assertThat(copy).hasSize(3);
        assertThat(copy.get("alpha")).isEqualTo("one");
        assertThat(copy.get("beta")).isEqualTo(Integer.valueOf(2));
        assertThat(copy.get("gamma")).isEqualTo("three");

        deserialized.put("delta", "four");
        copy.put("epsilon", "five");

        assertThat(original).doesNotContainKeys("delta", "epsilon");
        assertThat(deserialized.get("delta")).isEqualTo("four");
        assertThat(copy.get("epsilon")).isEqualTo("five");
    }

    private static byte[] serialize(FastMap map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
            stream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static FastMap deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object object = stream.readObject();
            assertThat(object).isInstanceOf(FastMap.class);
            return (FastMap) object;
        }
    }
}
