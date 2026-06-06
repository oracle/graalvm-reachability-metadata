/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.ReferenceMap;
import java.io.ByteArrayOutputStream;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ReferenceMapTest {

    @Test
    public void doesNotAdvertiseJavaSerializationSupport() {
        assertThat(Serializable.class.isAssignableFrom(ReferenceMap.class)).isFalse();
    }

    @Test
    public void javaSerializationRejectsNonSerializableReferenceMap() {
        ReferenceMap map = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.HARD);
        map.put("alpha", "one");

        assertThatThrownBy(() -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
                outputStream.writeObject(map);
            }
        }).isInstanceOf(NotSerializableException.class)
                .hasMessage(ReferenceMap.class.getName());
    }

    @Test
    public void storesAndRetrievesEntriesWithHardReferences() {
        ReferenceMap map = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.HARD);

        Object previousValue = map.put("primary", "one");
        Object replacedValue = map.put("primary", "two");
        map.put("secondary", "three");

        assertThat(previousValue).isNull();
        assertThat(replacedValue).isEqualTo("one");
        assertThat(map).hasSize(2);
        assertThat(map.get("primary")).isEqualTo("two");
        assertThat(map.containsKey("secondary")).isTrue();
        assertThat(map.isEmpty()).isFalse();
    }

    @Test
    public void exposesLiveMapViewsForHardReferences() {
        ReferenceMap map = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.HARD);
        map.put("alpha", "one");
        map.put("beta", "two");

        Set keySet = map.keySet();
        Collection values = map.values();
        Set entrySet = map.entrySet();

        assertThat(keySet).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(values).containsExactlyInAnyOrder("one", "two");
        assertThat(entrySet)
                .extracting(ReferenceMapTest::formatEntry)
                .containsExactlyInAnyOrder("alpha=one", "beta=two");

        assertThat(map.remove("alpha")).isEqualTo("one");
        assertThat(keySet).containsExactly("beta");
        assertThat(values).containsExactly("two");
    }

    private static String formatEntry(Object object) {
        Map.Entry entry = (Map.Entry) object;
        return entry.getKey() + "=" + entry.getValue();
    }

    @Test
    public void clearsAllMappings() {
        ReferenceMap map = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.HARD);
        map.put("alpha", "one");
        map.put("beta", "two");

        map.clear();

        assertThat(map).isEmpty();
        assertThat(map.get("alpha")).isNull();
        assertThat(map.entrySet()).isEmpty();
    }
}
