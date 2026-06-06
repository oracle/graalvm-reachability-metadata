/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.Predicate;
import io.sundr.deps.org.apache.commons.collections.PredicateUtils;
import io.sundr.deps.org.apache.commons.collections.map.PredicatedMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AbstractInputCheckedMapDecoratorInnerEntrySetTest {

    @Test
    public void toArrayWithTypedArrayReturnsCheckedEntryWrappers() {
        Map decorated = new LinkedHashMap();
        Predicate keyPredicate = PredicateUtils.instanceofPredicate(String.class);
        Predicate valuePredicate = PredicateUtils.notNullPredicate();
        Map map = PredicatedMap.decorate(decorated, keyPredicate, valuePredicate);
        map.put("alpha", "one");
        map.put("beta", "two");

        Map.Entry[] providedArray = new Map.Entry[3];
        Map.Entry[] entries = (Map.Entry[]) map.entrySet().toArray(providedArray);

        assertThat(entries).isSameAs(providedArray);
        assertThat(entries[0].getKey()).isEqualTo("alpha");
        assertThat(entries[1].getKey()).isEqualTo("beta");
        assertThat(entries[2]).isNull();

        assertThat(entries[0].setValue("updated-one")).isEqualTo("one");
        assertThat(map).containsEntry("alpha", "updated-one");
        assertThatThrownBy(() -> entries[1].setValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot set value");
    }
}
