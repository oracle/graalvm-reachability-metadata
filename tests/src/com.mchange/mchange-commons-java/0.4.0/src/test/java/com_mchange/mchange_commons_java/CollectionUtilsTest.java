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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
        PublicCloneCollection original = new PublicCloneCollection(List.of("alpha", "beta"));

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(PublicCloneCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCollectionUsingCollectionCopyConstructor() throws NoSuchMethodException {
        PublicCollectionCopyCollection original = new PublicCollectionCopyCollection(List.of("alpha", "beta"));

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(PublicCollectionCopyCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesCollectionUsingSameTypeCopyConstructor() throws NoSuchMethodException {
        SelfCopyCollection original = new SelfCopyCollection();
        original.add("alpha");
        original.add("beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(SelfCopyCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesMapUsingPublicCloneMethod() throws NoSuchMethodException {
        PublicCloneMap original = new PublicCloneMap(Map.of("one", 1, "two", 2));

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(PublicCloneMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesMapUsingMapCopyConstructor() throws NoSuchMethodException {
        PublicMapCopyMap original = new PublicMapCopyMap(Map.of("one", 1, "two", 2));

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(PublicMapCopyMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneClonesMapUsingSameTypeCopyConstructor() throws NoSuchMethodException {
        SelfCopyMap original = new SelfCopyMap();
        original.put("one", 1);
        original.put("two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(SelfCopyMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    public static final class PublicCloneCollection extends AbstractCollection<String> {
        private final Collection<String> values = new ArrayList<>();

        public PublicCloneCollection(Collection<String> values) {
            this.values.addAll(values);
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
        public Object clone() {
            return new PublicCloneCollection(values);
        }
    }

    public static final class PublicCollectionCopyCollection extends AbstractCollection<String> {
        private final Collection<String> values = new ArrayList<>();

        public PublicCollectionCopyCollection(Collection<String> values) {
            this.values.addAll(values);
        }

        @Override
        public Iterator<String> iterator() {
            return values.iterator();
        }

        @Override
        public int size() {
            return values.size();
        }
    }

    public static final class SelfCopyCollection extends AbstractCollection<String> {
        private final Collection<String> values = new ArrayList<>();

        public SelfCopyCollection() {
        }

        public SelfCopyCollection(SelfCopyCollection source) {
            values.addAll(source.values);
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

    public static final class PublicCloneMap extends AbstractMap<String, Integer> {
        private final Map<String, Integer> values = new LinkedHashMap<>();

        public PublicCloneMap(Map<String, Integer> values) {
            this.values.putAll(values);
        }

        @Override
        public Set<Entry<String, Integer>> entrySet() {
            return values.entrySet();
        }

        @Override
        public Object clone() {
            return new PublicCloneMap(values);
        }
    }

    public static final class PublicMapCopyMap extends AbstractMap<String, Integer> {
        private final Map<String, Integer> values = new LinkedHashMap<>();

        public PublicMapCopyMap(Map<String, Integer> values) {
            this.values.putAll(values);
        }

        @Override
        public Set<Entry<String, Integer>> entrySet() {
            return values.entrySet();
        }
    }

    public static final class SelfCopyMap extends AbstractMap<String, Integer> {
        private final Map<String, Integer> values = new LinkedHashMap<>();

        public SelfCopyMap() {
        }

        public SelfCopyMap(SelfCopyMap source) {
            values.putAll(source.values);
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
