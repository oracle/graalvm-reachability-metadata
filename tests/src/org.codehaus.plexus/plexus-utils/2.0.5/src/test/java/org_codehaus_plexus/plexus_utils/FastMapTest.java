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
    void roundTripsEntriesThroughJavaSerialization() throws Exception {
        FastMap original = new FastMap(4);
        original.put("alpha", "one");
        original.put("beta", Integer.valueOf(2));
        original.put("gamma", "three");

        byte[] serialized = serialize(original);
        FastMap copy = deserialize(serialized);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.capacity()).isEqualTo(original.capacity());
        assertThat(copy).hasSize(3);
        assertThat(copy.get("alpha")).isEqualTo("one");
        assertThat(copy.get("beta")).isEqualTo(Integer.valueOf(2));
        assertThat(copy.get("gamma")).isEqualTo("three");
        assertThat(copy.entrySet())
                .extracting(entry -> ((Map.Entry) entry).getKey())
                .containsExactly("alpha", "beta", "gamma");

        copy.put("delta", "four");

        assertThat(copy.get("delta")).isEqualTo("four");
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
