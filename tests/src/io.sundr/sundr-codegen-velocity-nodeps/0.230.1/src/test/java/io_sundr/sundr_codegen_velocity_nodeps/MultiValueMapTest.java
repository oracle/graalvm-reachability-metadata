/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.map.MultiValueMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MultiValueMapTest {

    @Test
    public void decoratesMapWithDefaultArrayListBackedValueCollections() {
        Map decorated = new HashMap();
        MultiValueMap map = MultiValueMap.decorate(decorated);

        Object firstValue = map.put("letters", "a");
        Object secondValue = map.put("letters", "b");
        Iterator values = map.iterator("letters");

        assertThat(firstValue).isEqualTo("a");
        assertThat(secondValue).isEqualTo("b");
        assertThat(map).hasSize(1);
        assertThat(map.totalSize()).isEqualTo(2);
        assertThat(map.size("letters")).isEqualTo(2);
        assertThat(map.getCollection("letters")).containsExactly("a", "b");
        assertThat(decorated).containsOnlyKeys("letters");
        assertThat(values.next()).isEqualTo("a");
        assertThat(values.next()).isEqualTo("b");
        assertThat(values.hasNext()).isFalse();
    }
}
