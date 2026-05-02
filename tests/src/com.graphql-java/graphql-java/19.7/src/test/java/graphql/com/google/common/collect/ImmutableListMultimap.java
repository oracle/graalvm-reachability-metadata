/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal relocated Guava list multimap required by graphql-java 19.7.
 */
public final class ImmutableListMultimap<K, V> implements Multimap<K, V> {

  private final Map<K, ImmutableList<V>> map;

  private ImmutableListMultimap(Map<K, ImmutableList<V>> map) {
    this.map = Collections.unmodifiableMap(map);
  }

  public static <K, V> ImmutableListMultimap<K, V> of() {
    return new ImmutableListMultimap<>(Map.of());
  }

  public static <K, V> Builder<K, V> builder() {
    return new Builder<>();
  }

  @Override
  public boolean put(K key, V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object key, Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ImmutableList<V> get(K key) {
    return map.getOrDefault(key, ImmutableList.of());
  }

  @Override
  public Map<K, Collection<V>> asMap() {
    Map<K, Collection<V>> result = new LinkedHashMap<>();
    result.putAll(map);
    return Collections.unmodifiableMap(result);
  }

  public int size() {
    int size = 0;
    for (List<V> values : map.values()) {
      size += values.size();
    }
    return size;
  }

  public static final class Builder<K, V> {

    private final Map<K, java.util.ArrayList<V>> values = new LinkedHashMap<>();

    public Builder<K, V> put(K key, V value) {
      values.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(value);
      return this;
    }

    public Builder<K, V> putAll(K key, Iterable<? extends V> newValues) {
      for (V value : newValues) {
        put(key, value);
      }
      return this;
    }

    public ImmutableListMultimap<K, V> build() {
      Map<K, ImmutableList<V>> immutableValues = new LinkedHashMap<>();
      for (Map.Entry<K, java.util.ArrayList<V>> entry : values.entrySet()) {
        immutableValues.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
      }
      return new ImmutableListMultimap<>(immutableValues);
    }
  }
}
