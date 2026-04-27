/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.tools.ant.types.selectors.cacheselector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.tools.ant.types.selectors.modifiedselector.Cache;

public class PropertiesfileCache implements Cache {
    private final Map<Object, Object> values = new HashMap<>();

    public PropertiesfileCache() {
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void delete() {
        values.clear();
    }

    @Override
    public void load() {
    }

    @Override
    public void save() {
    }

    @Override
    public Object get(Object key) {
        return values.get(key);
    }

    @Override
    public void put(Object key, Object value) {
        values.put(key, value);
    }

    @Override
    public Iterator<Object> iterator() {
        return values.keySet().iterator();
    }
}
