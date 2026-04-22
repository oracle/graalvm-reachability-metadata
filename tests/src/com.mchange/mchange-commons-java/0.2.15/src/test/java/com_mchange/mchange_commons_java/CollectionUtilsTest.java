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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionUtilsTest {
    @Test
    void attemptCloneUsesPublicCloneMethodForCollections() throws NoSuchMethodException {
        PublicCloneCollection original = new PublicCloneCollection(List.of("alpha", "beta"));

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(PublicCloneCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneUsesCollectionCopyConstructorWhenCloneIsUnavailable() throws NoSuchMethodException {
        CollectionCopyConstructorCollection original = new CollectionCopyConstructorCollection(List.of("alpha", "beta"));

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(CollectionCopyConstructorCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneUsesSameTypeCollectionConstructorAsFallback() throws NoSuchMethodException {
        SameTypeCopyCollection original = new SameTypeCopyCollection();
        original.add("alpha");
        original.add("beta");

        Collection<?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(SameTypeCopyCollection.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.contains("alpha")).isTrue();
        assertThat(cloned.contains("beta")).isTrue();
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneUsesPublicCloneMethodForMaps() throws NoSuchMethodException {
        PublicCloneMap original = new PublicCloneMap(Map.of("one", 1, "two", 2));

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(PublicCloneMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneUsesMapCopyConstructorWhenCloneIsUnavailable() throws NoSuchMethodException {
        MapCopyConstructorMap original = new MapCopyConstructorMap(Map.of("one", 1, "two", 2));

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(MapCopyConstructorMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void attemptCloneUsesSameTypeMapConstructorAsFallback() throws NoSuchMethodException {
        SameTypeCopyMap original = new SameTypeCopyMap();
        original.put("one", 1);
        original.put("two", 2);

        Map<?, ?> cloned = CollectionUtils.attemptClone(original);

        assertThat(cloned).isInstanceOf(SameTypeCopyMap.class);
        assertThat(cloned).hasSize(2);
        assertThat(cloned.get("one")).isEqualTo(1);
        assertThat(cloned.get("two")).isEqualTo(2);
        assertThat(cloned).isNotSameAs(original);
    }

    public static final class PublicCloneCollection extends AbstractCollection<String> {
        private final List<String> values;

        public PublicCloneCollection(Collection<String> values) {
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

        public Object clone() {
            return new PublicCloneCollection(values);
        }
    }

    public static final class CollectionCopyConstructorCollection extends AbstractCollection<String> {
        private final List<String> values;

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

    public static final class SameTypeCopyCollection extends AbstractCollection<String> {
        private final List<String> values;

        public SameTypeCopyCollection() {
            this.values = new ArrayList<>();
        }

        public SameTypeCopyCollection(SameTypeCopyCollection other) {
            this.values = new ArrayList<>(other.values);
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
        private final LinkedHashMap<String, Integer> values;

        public PublicCloneMap(Map<String, Integer> values) {
            this.values = new LinkedHashMap<>(values);
        }

        @Override
        public Set<Map.Entry<String, Integer>> entrySet() {
            return values.entrySet();
        }

        @Override
        public Integer put(String key, Integer value) {
            return values.put(key, value);
        }

        public Object clone() {
            return new PublicCloneMap(values);
        }
    }

    public static final class MapCopyConstructorMap extends AbstractMap<String, Integer> {
        private final LinkedHashMap<String, Integer> values;

        public MapCopyConstructorMap(Map<String, Integer> values) {
            this.values = new LinkedHashMap<>(values);
        }

        @Override
        public Set<Map.Entry<String, Integer>> entrySet() {
            return values.entrySet();
        }

        @Override
        public Integer put(String key, Integer value) {
            return values.put(key, value);
        }
    }

    public static final class SameTypeCopyMap extends AbstractMap<String, Integer> {
        private final LinkedHashMap<String, Integer> values;

        public SameTypeCopyMap() {
            this.values = new LinkedHashMap<>();
        }

        public SameTypeCopyMap(SameTypeCopyMap other) {
            this.values = new LinkedHashMap<>(other.values);
        }

        @Override
        public Set<Map.Entry<String, Integer>> entrySet() {
            return values.entrySet();
        }

        @Override
        public Integer put(String key, Integer value) {
            return values.put(key, value);
        }
    }
}
