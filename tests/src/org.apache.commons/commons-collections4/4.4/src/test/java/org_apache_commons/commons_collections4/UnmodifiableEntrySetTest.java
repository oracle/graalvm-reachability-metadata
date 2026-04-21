/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.map.UnmodifiableEntrySet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnmodifiableEntrySetTest {

    @Test
    void toArrayWithOversizedTypedArrayCopiesUnmodifiableEntriesIntoProvidedArray() {
        Set<Map.Entry<String, Integer>> entrySet = unmodifiableEntrySet();

        @SuppressWarnings("unchecked")
        Map.Entry<String, Integer>[] providedArray = (Map.Entry<String, Integer>[]) new Map.Entry[3];

        Map.Entry<String, Integer>[] entries = entrySet.toArray(providedArray);

        assertThat(entries).isSameAs(providedArray);
        assertThat(entries[0].getKey()).isEqualTo("alpha");
        assertThat(entries[0].getValue()).isEqualTo(1);
        assertThat(entries[1].getKey()).isEqualTo("beta");
        assertThat(entries[1].getValue()).isEqualTo(2);
        assertThat(entries[2]).isNull();
        assertThatThrownBy(() -> entries[0].setValue(10))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toArrayWithUndersizedTypedArrayReturnsNewTypedArrayOfUnmodifiableEntries() {
        Set<Map.Entry<String, Integer>> entrySet = unmodifiableEntrySet();

        @SuppressWarnings("unchecked")
        Map.Entry<String, Integer>[] providedArray = (Map.Entry<String, Integer>[]) new Map.Entry[1];

        Map.Entry<String, Integer>[] entries = entrySet.toArray(providedArray);

        assertThat(entries)
                .isNotSameAs(providedArray)
                .hasSize(2);
        assertThat(entries[0].getKey()).isEqualTo("alpha");
        assertThat(entries[0].getValue()).isEqualTo(1);
        assertThat(entries[1].getKey()).isEqualTo("beta");
        assertThat(entries[1].getValue()).isEqualTo(2);
        assertThatThrownBy(() -> entries[1].setValue(20))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static Set<Map.Entry<String, Integer>> unmodifiableEntrySet() {
        LinkedHashMap<String, Integer> delegate = new LinkedHashMap<>();
        delegate.put("alpha", 1);
        delegate.put("beta", 2);
        return UnmodifiableEntrySet.unmodifiableEntrySet(delegate.entrySet());
    }
}
