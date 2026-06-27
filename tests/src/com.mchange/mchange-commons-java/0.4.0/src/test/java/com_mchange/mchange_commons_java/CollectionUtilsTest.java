/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.util.CollectionUtils;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionUtilsTest {
    @Test
    void attemptCloneClonesArrayListUsingBuiltInPath() throws NoSuchMethodException {
        ArrayList<String> original = new ArrayList<>();
        original.add("alpha");
        original.add("beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(ArrayList.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesTreeSetUsingBuiltInPath() throws NoSuchMethodException {
        TreeSet<String> original = new TreeSet<>();
        original.add("beta");
        original.add("alpha");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(TreeSet.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesHashMapUsingBuiltInPath() throws NoSuchMethodException {
        HashMap<String, Integer> original = new HashMap<>();
        original.put("one", 1);
        original.put("two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(HashMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesTreeMapUsingBuiltInPath() throws NoSuchMethodException {
        TreeMap<String, Integer> original = new TreeMap<>();
        original.put("two", 2);
        original.put("one", 1);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(TreeMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCollectionUsingPublicCloneMethod() throws NoSuchMethodException {
        CloneableCollection original = new CloneableCollection("alpha", "beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(CloneableCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCollectionUsingCollectionCopyConstructor() throws NoSuchMethodException {
        CollectionCopyConstructorCollection original =
                new CollectionCopyConstructorCollection("alpha", "beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(CollectionCopyConstructorCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCollectionUsingExactClassCopyConstructor() throws NoSuchMethodException {
        ExactCopyConstructorCollection original =
                new ExactCopyConstructorCollection("alpha", "beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(ExactCopyConstructorCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesMapUsingPublicCloneMethod() throws NoSuchMethodException {
        CloneableMap original = new CloneableMap("one", 1, "two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(CloneableMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesMapUsingMapCopyConstructor() throws NoSuchMethodException {
        MapCopyConstructorMap original = new MapCopyConstructorMap("one", 1, "two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(MapCopyConstructorMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesMapUsingExactClassCopyConstructor() throws NoSuchMethodException {
        ExactCopyConstructorMap original = new ExactCopyConstructorMap("one", 1, "two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(ExactCopyConstructorMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    public static class CloneableCollection extends TestCollection {
        public CloneableCollection(String... elements) {
            super(elements);
        }

        private CloneableCollection(Collection<String> elements) {
            super(elements);
        }

        @Override
        public CloneableCollection clone() {
            return new CloneableCollection(values);
        }
    }

    public static class CollectionCopyConstructorCollection extends TestCollection {
        public CollectionCopyConstructorCollection(String... elements) {
            super(elements);
        }

        public CollectionCopyConstructorCollection(Collection<String> elements) {
            super(elements);
        }
    }

    public static class ExactCopyConstructorCollection extends TestCollection {
        public ExactCopyConstructorCollection(String... elements) {
            super(elements);
        }

        public ExactCopyConstructorCollection(ExactCopyConstructorCollection source) {
            super(source.values);
        }
    }

    public static class CloneableMap extends TestMap {
        public CloneableMap(
                String firstKey, Integer firstValue, String secondKey, Integer secondValue) {
            super(firstKey, firstValue, secondKey, secondValue);
        }

        private CloneableMap(Map<String, Integer> entries) {
            super(entries);
        }

        @Override
        public CloneableMap clone() {
            return new CloneableMap(values);
        }
    }

    public static class MapCopyConstructorMap extends TestMap {
        public MapCopyConstructorMap(
                String firstKey, Integer firstValue, String secondKey, Integer secondValue) {
            super(firstKey, firstValue, secondKey, secondValue);
        }

        public MapCopyConstructorMap(Map<String, Integer> entries) {
            super(entries);
        }
    }

    public static class ExactCopyConstructorMap extends TestMap {
        public ExactCopyConstructorMap(
                String firstKey, Integer firstValue, String secondKey, Integer secondValue) {
            super(firstKey, firstValue, secondKey, secondValue);
        }

        public ExactCopyConstructorMap(ExactCopyConstructorMap source) {
            super(source.values);
        }
    }

    private abstract static class TestCollection extends AbstractList<String> {
        protected final List<String> values = new ArrayList<>();

        TestCollection(String... elements) {
            Collections.addAll(values, elements);
        }

        TestCollection(Collection<String> elements) {
            values.addAll(elements);
        }

        @Override
        public String get(int index) {
            return values.get(index);
        }

        @Override
        public int size() {
            return values.size();
        }
    }

    private abstract static class TestMap extends AbstractMap<String, Integer> {
        protected final Map<String, Integer> values = new LinkedHashMap<>();

        TestMap(String firstKey, Integer firstValue, String secondKey, Integer secondValue) {
            values.put(firstKey, firstValue);
            values.put(secondKey, secondValue);
        }

        TestMap(Map<String, Integer> entries) {
            values.putAll(entries);
        }

        @Override
        public Set<Entry<String, Integer>> entrySet() {
            return values.entrySet();
        }
    }
}
