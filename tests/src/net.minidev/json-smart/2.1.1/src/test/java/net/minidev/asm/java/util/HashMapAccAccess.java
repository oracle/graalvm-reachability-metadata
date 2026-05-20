/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net.minidev.asm.java.util;

import java.util.HashMap;
import net.minidev.asm.BeansAccess;

public final class HashMapAccAccess extends BeansAccess<HashMap<Object, Object>> {
    @Override
    public void set(HashMap<Object, Object> object, int methodIndex, Object value) {
        object.put(getAccessors()[methodIndex].getName(), value);
    }

    @Override
    public Object get(HashMap<Object, Object> object, int methodIndex) {
        return object.get(getAccessors()[methodIndex].getName());
    }

    @Override
    public HashMap<Object, Object> newInstance() {
        return new HashMap<>();
    }
}
