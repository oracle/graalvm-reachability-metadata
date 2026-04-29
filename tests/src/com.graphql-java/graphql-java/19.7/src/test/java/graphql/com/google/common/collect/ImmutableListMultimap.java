/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ImmutableListMultimap<K, V> implements Multimap<K, V> {

  private final Map<K, ImmutableList<V>> delegate;

  private ImmutableListMultimap(Map<K, ImmutableList<V>> values) {
    this.delegate = ImmutableMap.copyOf(values);
  }

  public static <K, V> ImmutableListMultimap<K, V> of() {
    return new ImmutableListMultimap<>(new LinkedHashMap<>());
  }

  public static <K, V> Builder<K, V> builder() {
    return new Builder<>();
  }

  @Override
  public ImmutableList<V> get(K key) {
    return delegate.getOrDefault(key, ImmutableList.of());
  }

  @Override
  public boolean put(K key, V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object key, Object value) {
    throw new UnsupportedOperationException();
  }

  public static final class Builder<K, V> {

    private final Map<K, ImmutableList.Builder<V>> values = new LinkedHashMap<>();

    public Builder<K, V> put(K key, V value) {
      values.computeIfAbsent(key, unused -> ImmutableList.builder()).add(value);
      return this;
    }

    public ImmutableListMultimap<K, V> build() {
      Map<K, ImmutableList<V>> built = new LinkedHashMap<>();
      for (Map.Entry<K, ImmutableList.Builder<V>> entry : values.entrySet()) {
        built.put(entry.getKey(), entry.getValue().build());
      }
      return new ImmutableListMultimap<>(built);
    }
  }
}
