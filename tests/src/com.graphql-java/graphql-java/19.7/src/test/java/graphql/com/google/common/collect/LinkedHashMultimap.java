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
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Minimal relocated Guava linked hash multimap required by graphql-java 19.7.
 */
public final class LinkedHashMultimap<K, V> implements Multimap<K, V> {

  private final Map<K, Collection<V>> map = new LinkedHashMap<>();

  private LinkedHashMultimap() {
  }

  public static <K, V> LinkedHashMultimap<K, V> create() {
    return new LinkedHashMultimap<>();
  }

  @Override
  public boolean put(K key, V value) {
    return map.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(value);
  }

  @Override
  public boolean remove(Object key, Object value) {
    Collection<V> values = map.get(key);
    if (values == null) {
      return false;
    }
    boolean removed = values.remove(value);
    if (values.isEmpty()) {
      map.remove(key);
    }
    return removed;
  }

  @Override
  public Collection<V> get(K key) {
    return map.getOrDefault(key, Collections.emptySet());
  }

  @Override
  public Map<K, Collection<V>> asMap() {
    return Collections.unmodifiableMap(map);
  }
}
