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

public final class ImmutableMap<K, V> extends AbstractMap<K, V> {

  private final Map<K, V> delegate;

  private ImmutableMap(Map<? extends K, ? extends V> values) {
    this.delegate = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  public static <K, V> ImmutableMap<K, V> of() {
    return new ImmutableMap<>(Collections.emptyMap());
  }

  public static <K, V> ImmutableMap<K, V> of(K firstKey, V firstValue, K secondKey, V secondValue,
          K thirdKey, V thirdValue, K fourthKey, V fourthValue) {
    Builder<K, V> builder = builder();
    return builder.put(firstKey, firstValue).put(secondKey, secondValue).put(thirdKey, thirdValue)
            .put(fourthKey, fourthValue).build();
  }

  public static <K, V> ImmutableMap<K, V> of(K firstKey, V firstValue, K secondKey, V secondValue,
          K thirdKey, V thirdValue, K fourthKey, V fourthValue, K fifthKey, V fifthValue) {
    Builder<K, V> builder = builder();
    return builder.put(firstKey, firstValue).put(secondKey, secondValue).put(thirdKey, thirdValue)
            .put(fourthKey, fourthValue).put(fifthKey, fifthValue).build();
  }

  public static <K, V> ImmutableMap<K, V> copyOf(Map<? extends K, ? extends V> values) {
    return new ImmutableMap<>(values);
  }

  public static <K, V> Builder<K, V> builder() {
    return new Builder<>();
  }

  @Override
  public boolean containsKey(Object key) {
    return delegate.containsKey(key);
  }

  @Override
  public V get(Object key) {
    return delegate.get(key);
  }

  @Override
  public V getOrDefault(Object key, V defaultValue) {
    return delegate.getOrDefault(key, defaultValue);
  }

  @Override
  public ImmutableSet<Entry<K, V>> entrySet() {
    return ImmutableSet.copyOf(delegate.entrySet());
  }

  @Override
  public ImmutableSet<K> keySet() {
    return ImmutableSet.copyOf(delegate.keySet());
  }

  @Override
  public ImmutableCollection<V> values() {
    return new ImmutableCollection<>(delegate.values());
  }

  @Override
  public int size() {
    return delegate.size();
  }

  public static final class Builder<K, V> {

    private final Map<K, V> values = new LinkedHashMap<>();

    public Builder<K, V> put(K key, V value) {
      values.put(key, value);
      return this;
    }

    public Builder<K, V> putAll(Map<? extends K, ? extends V> map) {
      values.putAll(map);
      return this;
    }

    public ImmutableMap<K, V> build() {
      return ImmutableMap.copyOf(values);
    }
  }
}
