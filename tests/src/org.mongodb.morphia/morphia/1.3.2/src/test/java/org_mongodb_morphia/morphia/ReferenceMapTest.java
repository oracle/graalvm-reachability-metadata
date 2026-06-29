/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import org.junit.jupiter.api.Test;
import relocated.morphia.org.apache.commons.collections.ReferenceMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ReferenceMapTest {
    @Test
    public void storesAndRemovesHardReferencedEntries() {
        final ReferenceMap map = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.HARD);

        map.put("morphia", "mongodb");
        map.put("driver", "legacy");

        assertThat(map)
                .containsEntry("morphia", "mongodb")
                .containsEntry("driver", "legacy")
                .hasSize(2);
        assertThat(map.remove("driver")).isEqualTo("legacy");
        assertThat(map)
                .containsOnlyKeys("morphia")
                .hasSize(1);
    }

    @Test
    public void doesNotAdvertiseJavaSerializationSupport() {
        final ReferenceMap map = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.HARD);
        map.put("morphia", "mongodb");

        // ReferenceMap declares private serialization hooks, but Java serialization cannot reach them.
        assertThat(Serializable.class.isAssignableFrom(ReferenceMap.class)).isFalse();
        assertThat(ObjectStreamClass.lookup(ReferenceMap.class)).isNull();
        assertThatThrownBy(() -> serialize(map))
                .isInstanceOf(NotSerializableException.class)
                .hasMessage(ReferenceMap.class.getName());
    }

    private static byte[] serialize(Object value) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }
}
