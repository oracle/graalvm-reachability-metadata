/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_testsupport.org_apache_aries_testsupport_unit;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

final class MapDictionary<K, V> extends Dictionary<K, V> {
    private final Map<K, V> values = new HashMap<>();

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public Enumeration<K> keys() {
        return Collections.enumeration(values.keySet());
    }

    @Override
    public Enumeration<V> elements() {
        return Collections.enumeration(values.values());
    }

    @Override
    public V get(Object key) {
        return values.get(key);
    }

    @Override
    public V put(K key, V value) {
        return values.put(key, value);
    }

    @Override
    public V remove(Object key) {
        return values.remove(key);
    }
}
