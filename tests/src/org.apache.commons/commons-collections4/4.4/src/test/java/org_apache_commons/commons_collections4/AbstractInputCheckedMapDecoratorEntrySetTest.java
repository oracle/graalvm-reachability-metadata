/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.collections4.functors.NotNullPredicate;
import org.apache.commons.collections4.map.PredicatedMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractInputCheckedMapDecoratorEntrySetTest {

    @Test
    void toArrayWithOversizedTypedArrayCopiesWrappedEntriesBackIntoProvidedArray() {
        PredicatedMap<String, String> map = PredicatedMap.predicatedMap(
                new LinkedHashMap<>(),
                NotNullPredicate.notNullPredicate(),
                NotNullPredicate.notNullPredicate());
        map.put("alpha", "one");
        map.put("beta", "two");

        @SuppressWarnings("unchecked")
        Map.Entry<String, String>[] providedArray = (Map.Entry<String, String>[]) new Map.Entry[3];

        Map.Entry<String, String>[] entries = map.entrySet().toArray(providedArray);

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

    @Test
    void toArrayWithUndersizedTypedArrayReturnsNewTypedArrayWithWrappedEntries() {
        PredicatedMap<String, String> map = PredicatedMap.predicatedMap(
                new LinkedHashMap<>(),
                NotNullPredicate.notNullPredicate(),
                NotNullPredicate.notNullPredicate());
        map.put("alpha", "one");
        map.put("beta", "two");

        @SuppressWarnings("unchecked")
        Map.Entry<String, String>[] providedArray = (Map.Entry<String, String>[]) new Map.Entry[1];

        Map.Entry<String, String>[] entries = map.entrySet().toArray(providedArray);

        assertThat(entries)
                .isNotSameAs(providedArray)
                .hasSize(2);
        assertThat(entries[0].getKey()).isEqualTo("alpha");
        assertThat(entries[1].getKey()).isEqualTo("beta");

        assertThat(entries[1].setValue("updated-two")).isEqualTo("two");
        assertThat(map).containsEntry("beta", "updated-two");
        assertThatThrownBy(() -> entries[0].setValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot set value");
    }
}
