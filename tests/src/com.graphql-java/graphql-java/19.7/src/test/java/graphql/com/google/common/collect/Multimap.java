/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.Collection;
import java.util.Map;

/**
 * Minimal relocated Guava multimap API required by graphql-java 19.7.
 */
public interface Multimap<K, V> {

  boolean put(K key, V value);

  boolean remove(Object key, Object value);

  Collection<V> get(K key);

  Map<K, Collection<V>> asMap();
}
