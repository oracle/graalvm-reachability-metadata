/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal relocated Guava map API required by graphql-java 19.7.
 */
public final class ImmutableMap<K, V> extends AbstractMap<K, V> {

  private final Map<K, V> map;

  private ImmutableMap(Map<K, V> map) {
    this.map = map;
  }

  public static <K, V> ImmutableMap<K, V> of() {
    return new ImmutableMap<>(Map.of());
  }

  public static <K, V> ImmutableMap<K, V> of(K key, V value) {
    return copyOf(Map.of(key, value));
  }

  public static <K, V> ImmutableMap<K, V> of(K key1, V value1, K key2, V value2) {
    LinkedHashMap<K, V> values = new LinkedHashMap<>();
    values.put(key1, value1);
    values.put(key2, value2);
    return copyOf(values);
  }

  public static <K, V> ImmutableMap<K, V> of(K key1, V value1, K key2, V value2, K key3, V value3) {
    LinkedHashMap<K, V> values = new LinkedHashMap<>();
    values.put(key1, value1);
    values.put(key2, value2);
    values.put(key3, value3);
    return copyOf(values);
  }

  public static <K, V> ImmutableMap<K, V> of(K key1, V value1, K key2, V value2, K key3, V value3,
          K key4, V value4) {
    LinkedHashMap<K, V> values = new LinkedHashMap<>();
    values.put(key1, value1);
    values.put(key2, value2);
    values.put(key3, value3);
    values.put(key4, value4);
    return copyOf(values);
  }

  public static <K, V> ImmutableMap<K, V> of(K key1, V value1, K key2, V value2, K key3, V value3,
          K key4, V value4, K key5, V value5) {
    LinkedHashMap<K, V> values = new LinkedHashMap<>();
    values.put(key1, value1);
    values.put(key2, value2);
    values.put(key3, value3);
    values.put(key4, value4);
    values.put(key5, value5);
    return copyOf(values);
  }

  public static <K, V> ImmutableMap<K, V> copyOf(Map<? extends K, ? extends V> source) {
    return new ImmutableMap<>(Collections.unmodifiableMap(new LinkedHashMap<>(source)));
  }

  public static <K, V> Builder<K, V> builder() {
    return new Builder<>();
  }

  public static <K, V> Builder<K, V> builderWithExpectedSize(int expectedSize) {
    return new Builder<>(expectedSize);
  }

  @Override
  public ImmutableSet<Entry<K, V>> entrySet() {
    return ImmutableSet.copyOf(map.entrySet());
  }

  @Override
  public ImmutableSet<K> keySet() {
    return ImmutableSet.copyOf(map.keySet());
  }

  @Override
  public ImmutableCollection<V> values() {
    return ImmutableList.copyOf(map.values());
  }

  @Override
  public V get(Object key) {
    return map.get(key);
  }

  @Override
  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return map.containsValue(value);
  }

  @Override
  public int size() {
    return map.size();
  }

  public static final class Builder<K, V> {

    private final LinkedHashMap<K, V> values;

    public Builder() {
      this.values = new LinkedHashMap<>();
    }

    private Builder(int expectedSize) {
      this.values = new LinkedHashMap<>(expectedSize);
    }

    public Builder<K, V> put(K key, V value) {
      values.put(key, value);
      return this;
    }

    public Builder<K, V> put(Map.Entry<? extends K, ? extends V> entry) {
      values.put(entry.getKey(), entry.getValue());
      return this;
    }

    public Builder<K, V> putAll(Map<? extends K, ? extends V> source) {
      values.putAll(source);
      return this;
    }

    public ImmutableMap<K, V> build() {
      return ImmutableMap.copyOf(values);
    }
  }
}
