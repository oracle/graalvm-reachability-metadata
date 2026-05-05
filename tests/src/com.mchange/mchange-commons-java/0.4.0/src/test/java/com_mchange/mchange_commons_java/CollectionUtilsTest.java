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
    void attemptCloneClonesCustomCollectionUsingPublicCloneMethod() throws NoSuchMethodException {
        PublicCloneCollection<String> original = new PublicCloneCollection<>();
        original.add("alpha");
        original.add("beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(PublicCloneCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCustomCollectionUsingCollectionCopyConstructor() throws NoSuchMethodException {
        CollectionConstructorCollection<String> original = new CollectionConstructorCollection<>();
        original.add("one");
        original.add("two");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(CollectionConstructorCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("one")).isTrue();
        assertThat(cloned.contains("two")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCustomCollectionUsingSameTypeCopyConstructor() throws NoSuchMethodException {
        SameTypeConstructorCollection<String> original = new SameTypeConstructorCollection<>();
        original.add("red");
        original.add("blue");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(SameTypeConstructorCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("red")).isTrue();
        assertThat(cloned.contains("blue")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCustomMapUsingPublicCloneMethod() throws NoSuchMethodException {
        PublicCloneMap<String, Integer> original = new PublicCloneMap<>();
        original.put("one", 1);
        original.put("two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(PublicCloneMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCustomMapUsingMapCopyConstructor() throws NoSuchMethodException {
        MapConstructorMap<String, Integer> original = new MapConstructorMap<>();
        original.put("alpha", 10);
        original.put("beta", 20);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(MapConstructorMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("alpha")).isEqualTo(10);
        assertThat(cloned.get("beta")).isEqualTo(20);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCustomMapUsingSameTypeCopyConstructor() throws NoSuchMethodException {
        SameTypeConstructorMap<String, Integer> original = new SameTypeConstructorMap<>();
        original.put("left", 100);
        original.put("right", 200);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(SameTypeConstructorMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("left")).isEqualTo(100);
        assertThat(cloned.get("right")).isEqualTo(200);
        assertThat(cloned).isNotSameAs(original);
    }

    public static final class PublicCloneCollection<E> extends AbstractCollection<E> implements Cloneable {
        private final ArrayList<E> values = new ArrayList<>();

        @Override
        public boolean add(E element) {
            return values.add(element);
        }

        @Override
        public Iterator<E> iterator() {
            return values.iterator();
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public PublicCloneCollection<E> clone() {
            PublicCloneCollection<E> copy = new PublicCloneCollection<>();
            copy.values.addAll(values);
            return copy;
        }
    }

    public static final class CollectionConstructorCollection<E> extends AbstractCollection<E> {
        private final ArrayList<E> values = new ArrayList<>();

        public CollectionConstructorCollection() {
        }

        public CollectionConstructorCollection(Collection<? extends E> source) {
            values.addAll(source);
        }

        @Override
        public boolean add(E element) {
            return values.add(element);
        }

        @Override
        public Iterator<E> iterator() {
            return values.iterator();
        }

        @Override
        public int size() {
            return values.size();
        }
    }

    public static final class SameTypeConstructorCollection<E> extends AbstractCollection<E> {
        private final ArrayList<E> values = new ArrayList<>();

        public SameTypeConstructorCollection() {
        }

        public SameTypeConstructorCollection(SameTypeConstructorCollection<? extends E> source) {
            values.addAll(source.values);
        }

        @Override
        public boolean add(E element) {
            return values.add(element);
        }

        @Override
        public Iterator<E> iterator() {
            return values.iterator();
        }

        @Override
        public int size() {
            return values.size();
        }
    }

    public static final class PublicCloneMap<K, V> extends AbstractMap<K, V> implements Cloneable {
        private final LinkedHashMap<K, V> values = new LinkedHashMap<>();

        @Override
        public V put(K key, V value) {
            return values.put(key, value);
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return values.entrySet();
        }

        @Override
        public PublicCloneMap<K, V> clone() {
            PublicCloneMap<K, V> copy = new PublicCloneMap<>();
            copy.values.putAll(values);
            return copy;
        }
    }

    public static final class MapConstructorMap<K, V> extends AbstractMap<K, V> {
        private final LinkedHashMap<K, V> values = new LinkedHashMap<>();

        public MapConstructorMap() {
        }

        public MapConstructorMap(Map<? extends K, ? extends V> source) {
            values.putAll(source);
        }

        @Override
        public V put(K key, V value) {
            return values.put(key, value);
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return values.entrySet();
        }
    }

    public static final class SameTypeConstructorMap<K, V> extends AbstractMap<K, V> {
        private final LinkedHashMap<K, V> values = new LinkedHashMap<>();

        public SameTypeConstructorMap() {
        }

        public SameTypeConstructorMap(SameTypeConstructorMap<? extends K, ? extends V> source) {
            values.putAll(source.values);
        }

        @Override
        public V put(K key, V value) {
            return values.put(key, value);
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return values.entrySet();
        }
    }
}
