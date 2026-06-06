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
        ReflectionCloneCollection original = new ReflectionCloneCollection();
        original.add("alpha");
        original.add("beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(ReflectionCloneCollection.class);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCollectionUsingCollectionConstructor() throws NoSuchMethodException {
        CollectionConstructorCollection original = new CollectionConstructorCollection();
        original.add("alpha");
        original.add("beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(CollectionConstructorCollection.class);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCollectionUsingSameClassConstructor() throws NoSuchMethodException {
        SameClassConstructorCollection original = new SameClassConstructorCollection();
        original.add("alpha");
        original.add("beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(SameClassConstructorCollection.class);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesMapUsingPublicCloneMethod() throws NoSuchMethodException {
        ReflectionCloneMap original = new ReflectionCloneMap();
        original.put("one", 1);
        original.put("two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(ReflectionCloneMap.class);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesMapUsingMapConstructor() throws NoSuchMethodException {
        MapConstructorMap original = new MapConstructorMap();
        original.put("one", 1);
        original.put("two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(MapConstructorMap.class);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesMapUsingSameClassConstructor() throws NoSuchMethodException {
        SameClassConstructorMap original = new SameClassConstructorMap();
        original.put("one", 1);
        original.put("two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(SameClassConstructorMap.class);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    public static class ReflectionCloneCollection extends TestCollection {
        public ReflectionCloneCollection() {
        }

        @Override
        public ReflectionCloneCollection clone() {
            ReflectionCloneCollection clone = new ReflectionCloneCollection();
            clone.values.addAll(values);
            return clone;
        }
    }

    public static class CollectionConstructorCollection extends TestCollection {
        public CollectionConstructorCollection() {
        }

        public CollectionConstructorCollection(Collection<String> source) {
            values.addAll(source);
        }
    }

    public static class SameClassConstructorCollection extends TestCollection {
        public SameClassConstructorCollection() {
        }

        public SameClassConstructorCollection(SameClassConstructorCollection source) {
            values.addAll(source.values);
        }
    }

    public abstract static class TestCollection extends AbstractCollection<String> {
        protected final Collection<String> values = new ArrayList<>();

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

    public static class ReflectionCloneMap extends TestMap {
        public ReflectionCloneMap() {
        }

        @Override
        public ReflectionCloneMap clone() {
            ReflectionCloneMap clone = new ReflectionCloneMap();
            clone.values.putAll(values);
            return clone;
        }
    }

    public static class MapConstructorMap extends TestMap {
        public MapConstructorMap() {
        }

        public MapConstructorMap(Map<String, Integer> source) {
            values.putAll(source);
        }
    }

    public static class SameClassConstructorMap extends TestMap {
        public SameClassConstructorMap() {
        }

        public SameClassConstructorMap(SameClassConstructorMap source) {
            values.putAll(source.values);
        }
    }

    public abstract static class TestMap extends AbstractMap<String, Integer> {
        protected final Map<String, Integer> values = new HashMap<>();

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
