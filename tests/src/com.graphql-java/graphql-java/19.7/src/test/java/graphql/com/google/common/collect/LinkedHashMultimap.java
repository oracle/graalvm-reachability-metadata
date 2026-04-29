/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class LinkedHashMultimap<K, V> implements Multimap<K, V> {

  private final Map<K, Set<V>> delegate = new LinkedHashMap<>();

  private LinkedHashMultimap() {
  }

  public static <K, V> LinkedHashMultimap<K, V> create() {
    return new LinkedHashMultimap<>();
  }

  @Override
  public Collection<V> get(K key) {
    return delegate.computeIfAbsent(key, unused -> new LinkedHashSet<>());
  }

  @Override
  public boolean put(K key, V value) {
    return delegate.computeIfAbsent(key, unused -> new LinkedHashSet<>()).add(value);
  }

  @Override
  public boolean remove(Object key, Object value) {
    Set<V> values = delegate.get(key);
    if (values == null) {
      return false;
    }
    boolean removed = values.remove(value);
    if (values.isEmpty()) {
      delegate.remove(key);
    }
    return removed;
  }
}
