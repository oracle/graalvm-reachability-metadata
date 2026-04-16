/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mchange.v2.util.CollectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionUtilsTest {
    @Test
    void attemptCloneUsesPublicCloneMethodForCustomCollection() throws Exception {
        CloneableCollection original = new CloneableCollection(List.of("alpha", "beta"));

        Collection<?> clone = CollectionUtils.attemptClone(original);

        assertThat(clone)
            .isInstanceOf(CloneableCollection.class)
            .isNotSameAs(original)
            .containsExactly("alpha", "beta");
    }

    @Test
    void attemptCloneUsesCollectionCopyConstructorWhenCloneIsUnavailable() throws Exception {
        CollectionCopyConstructorCollection original =
            new CollectionCopyConstructorCollection(List.of("alpha", "beta"));

        Collection<?> clone = CollectionUtils.attemptClone(original);

        assertThat(clone)
            .isInstanceOf(CollectionCopyConstructorCollection.class)
            .isNotSameAs(original)
            .containsExactly("alpha", "beta");
    }

    @Test
    void attemptCloneUsesSameTypeCopyConstructorForCollectionWhenGenericCopyConstructorIsUnavailable()
        throws Exception {
        SelfCopyConstructorCollection original = new SelfCopyConstructorCollection();
        original.add("alpha");
        original.add("beta");

        Collection<?> clone = CollectionUtils.attemptClone(original);

        assertThat(clone)
            .isInstanceOf(SelfCopyConstructorCollection.class)
            .isNotSameAs(original)
            .containsExactly("alpha", "beta");
    }

    @Test
    void attemptCloneUsesPublicCloneMethodForCustomMap() throws Exception {
        CloneableMap original = new CloneableMap(Map.of("alpha", 1, "beta", 2));

        Map<?, ?> clone = CollectionUtils.attemptClone(original);

        assertThat(clone)
            .isInstanceOf(CloneableMap.class)
            .isNotSameAs(original)
            .containsEntry("alpha", 1)
            .containsEntry("beta", 2);
    }

    @Test
    void attemptCloneUsesMapCopyConstructorWhenCloneIsUnavailable() throws Exception {
        MapCopyConstructorMap original = new MapCopyConstructorMap(Map.of("alpha", 1, "beta", 2));

        Map<?, ?> clone = CollectionUtils.attemptClone(original);

        assertThat(clone)
            .isInstanceOf(MapCopyConstructorMap.class)
            .isNotSameAs(original)
            .containsEntry("alpha", 1)
            .containsEntry("beta", 2);
    }

    @Test
    void attemptCloneUsesSameTypeCopyConstructorForMapWhenGenericCopyConstructorIsUnavailable()
        throws Exception {
        SelfCopyConstructorMap original = new SelfCopyConstructorMap();
        original.put("alpha", 1);
        original.put("beta", 2);

        Map<?, ?> clone = CollectionUtils.attemptClone(original);

        assertThat(clone)
            .isInstanceOf(SelfCopyConstructorMap.class)
            .isNotSameAs(original)
            .containsEntry("alpha", 1)
            .containsEntry("beta", 2);
    }

    public static final class CloneableCollection extends AbstractCollection<String> implements Cloneable {
        private final List<String> values;

        public CloneableCollection() {
            this.values = new ArrayList<>();
        }

        public CloneableCollection(Collection<String> source) {
            this.values = new ArrayList<>(source);
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
        public CloneableCollection clone() {
            return new CloneableCollection(values);
        }
    }

    public static final class CollectionCopyConstructorCollection extends AbstractCollection<String> {
        private final List<String> values;

        public CollectionCopyConstructorCollection() {
            this.values = new ArrayList<>();
        }

        public CollectionCopyConstructorCollection(Collection<String> source) {
            this.values = new ArrayList<>(source);
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
        private final List<String> values;

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

        public CloneableMap(Map<String, Integer> source) {
            this.values = new LinkedHashMap<>(source);
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
        public CloneableMap clone() {
            return new CloneableMap(values);
        }
    }

    public static final class MapCopyConstructorMap extends AbstractMap<String, Integer> {
        private final LinkedHashMap<String, Integer> values;

        public MapCopyConstructorMap() {
            this.values = new LinkedHashMap<>();
        }

        public MapCopyConstructorMap(Map<String, Integer> source) {
            this.values = new LinkedHashMap<>(source);
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
