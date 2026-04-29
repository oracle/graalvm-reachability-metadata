/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.Collection;

public interface Multimap<K, V> {

  Collection<V> get(K key);

  boolean put(K key, V value);

  boolean remove(Object key, Object value);
}
