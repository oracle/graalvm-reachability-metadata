/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_assertj.assertj_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

public class MapsTest {
    @Test
    void preservesComparatorWhenCloningAMapForKeyAssertions() {
        TreeMap<String, Integer> inventory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        inventory.put("BOLT", 12);
        inventory.put("NUT", 24);
        assertThat(inventory).containsOnlyKeys("bolt", "nut");
        assertThat(inventory).containsEntry("BOLT", 12).hasSize(2);
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertThat(inventory).containsOnlyKeys("bolt"));
        assertThat(failure).hasMessageContaining("NUT");
        assertThat(inventory).hasSize(2);
    }

    @Test
    void preservesMapSemanticsThroughTheCopyConstructor() {
        CopyableCaseInsensitiveMap inventory = new CopyableCaseInsensitiveMap(Map.of("BOLT", 12));
        assertThat(inventory).containsOnlyKeys("bolt");
        assertThat(inventory).containsEntry("BOLT", 12).hasSize(1);
    }

    @Test
    void preservesMapSemanticsThroughTheDefaultConstructor() {
        CaseInsensitiveMap inventory = new CaseInsensitiveMap();
        inventory.put("NUT", 24);
        assertThat(inventory).containsOnlyKeys("nut");
        assertThat(inventory).containsEntry("NUT", 24).hasSize(1);
    }

    public static class CaseInsensitiveMap extends AbstractMap<String, Integer> {
        private final Map<String, Integer> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        public CaseInsensitiveMap() {
        }

        @Override
        public Set<Entry<String, Integer>> entrySet() {
            return values.entrySet();
        }

        @Override
        public Integer put(String key, Integer value) {
            return values.put(key, value);
        }

        @Override
        public boolean containsKey(Object key) {
            return values.containsKey(key);
        }

        @Override
        public Integer get(Object key) {
            return values.get(key);
        }

        @Override
        public Integer remove(Object key) {
            return values.remove(key);
        }
    }

    public static class CopyableCaseInsensitiveMap extends CaseInsensitiveMap {
        public CopyableCaseInsensitiveMap(Map<String, Integer> values) {
            putAll(values);
        }
    }
}
