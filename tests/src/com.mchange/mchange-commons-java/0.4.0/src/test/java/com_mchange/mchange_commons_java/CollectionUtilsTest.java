/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.util.CollectionUtils;
import org.junit.jupiter.api.Test;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
    void attemptCloneClonesCollectionWithPublicCloneMethod() throws NoSuchMethodException {
        CloneableCollection original = new CloneableCollection();
        original.add("alpha");
        original.add("beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(CloneableCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCollectionWithCollectionCopyConstructor() throws NoSuchMethodException {
        CollectionCopyConstructorCollection original = new CollectionCopyConstructorCollection();
        original.add("alpha");
        original.add("beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(CollectionCopyConstructorCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCollectionWithSelfCopyConstructor() throws NoSuchMethodException {
        SelfCopyConstructorCollection original = new SelfCopyConstructorCollection();
        original.add("alpha");
        original.add("beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(SelfCopyConstructorCollection.class);
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
    void attemptCloneClonesMapWithPublicCloneMethod() throws NoSuchMethodException {
        CloneableMap original = new CloneableMap();
        original.put("one", 1);
        original.put("two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(CloneableMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesMapWithMapCopyConstructor() throws NoSuchMethodException {
        MapCopyConstructorMap original = new MapCopyConstructorMap();
        original.put("one", 1);
        original.put("two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(MapCopyConstructorMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesMapWithSelfCopyConstructor() throws NoSuchMethodException {
        SelfCopyConstructorMap original = new SelfCopyConstructorMap();
        original.put("one", 1);
        original.put("two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(SelfCopyConstructorMap.class);
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

    public static final class CloneableCollection extends AbstractCollection<String> implements Cloneable {
        private final ArrayList<String> values;

        public CloneableCollection() {
            this.values = new ArrayList<>();
        }

        private CloneableCollection(Collection<String> values) {
            this.values = new ArrayList<>(values);
        }

        @Override
        public Iterator<String> iterator() {
            return values.iterator();
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public boolean add(String value) {
            return values.add(value);
        }

        @Override
        public Object clone() {
            return new CloneableCollection(values);
        }
    }

    public static final class CollectionCopyConstructorCollection extends AbstractCollection<String> {
        private final ArrayList<String> values;

        public CollectionCopyConstructorCollection() {
            this.values = new ArrayList<>();
        }

        public CollectionCopyConstructorCollection(Collection<String> values) {
            this.values = new ArrayList<>(values);
        }

        @Override
        public Iterator<String> iterator() {
            return values.iterator();
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public boolean add(String value) {
            return values.add(value);
        }
    }

    public static final class SelfCopyConstructorCollection extends AbstractCollection<String> {
        private final ArrayList<String> values;

        public SelfCopyConstructorCollection() {
            this.values = new ArrayList<>();
        }

        public SelfCopyConstructorCollection(SelfCopyConstructorCollection source) {
            this.values = new ArrayList<>(source.values);
        }

        @Override
        public Iterator<String> iterator() {
            return values.iterator();
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public boolean add(String value) {
            return values.add(value);
        }
    }

    public static final class CloneableMap extends AbstractMap<String, Integer> implements Cloneable {
        private final LinkedHashMap<String, Integer> values;

        public CloneableMap() {
            this.values = new LinkedHashMap<>();
        }

        private CloneableMap(Map<String, Integer> values) {
            this.values = new LinkedHashMap<>(values);
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
        public Object clone() {
            return new CloneableMap(values);
        }
    }

    public static final class MapCopyConstructorMap extends AbstractMap<String, Integer> {
        private final LinkedHashMap<String, Integer> values;

        public MapCopyConstructorMap() {
            this.values = new LinkedHashMap<>();
        }

        public MapCopyConstructorMap(Map<String, Integer> values) {
            this.values = new LinkedHashMap<>(values);
        }

        @Override
        public Set<Entry<String, Integer>> entrySet() {
            return values.entrySet();
        }

        @Override
        public Integer put(String key, Integer value) {
            return values.put(key, value);
        }
    }

    public static final class SelfCopyConstructorMap extends AbstractMap<String, Integer> {
        private final LinkedHashMap<String, Integer> values;

        public SelfCopyConstructorMap() {
            this.values = new LinkedHashMap<>();
        }

        public SelfCopyConstructorMap(SelfCopyConstructorMap source) {
            this.values = new LinkedHashMap<>(source.values);
        }

        @Override
        public Set<Entry<String, Integer>> entrySet() {
            return values.entrySet();
        }

        @Override
        public Integer put(String key, Integer value) {
            return values.put(key, value);
        }
    }
}
