/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.json_smart;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import net.minidev.asm.BeansAccess;
import net.minidev.json.JSONNavi;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import net.minidev.json.writer.JsonReaderI;
import org.junit.jupiter.api.Test;

public class CollectionMapperInnerMapTypeTest {
    @Test
    void parsesObjectIntoParameterizedMapType() {
        ParameterizedType mapType =
                new SimpleParameterizedType(AccessibleJsonObject.class, String.class, Integer.class);
        JsonReaderI<AccessibleJsonObject> mapper = JSONValue.defaultReader.getMapper(mapType);

        JSONNavi<AccessibleJsonObject> navi = new JSONNavi<>("{\"one\":1,\"two\":2}", mapper);

        assertThat(navi.getCurrentObject())
                .isInstanceOf(AccessibleJsonObject.class)
                .isEqualTo(expectedMap());
    }

    private static AccessibleJsonObject expectedMap() {
        AccessibleJsonObject expected = new AccessibleJsonObject();
        expected.put("one", 1);
        expected.put("two", 2);
        return expected;
    }

    // json-smart resolves AccAccess helpers with the raw type's class loader.
    public static class AccessibleJsonObject extends JSONObject {
    }

    public static class AccessibleJsonObjectAccAccess extends BeansAccess<AccessibleJsonObject> {
        @Override
        public void set(AccessibleJsonObject object, int methodIndex, Object value) {
            object.put(getAccessors()[methodIndex].getName(), value);
        }

        @Override
        public Object get(AccessibleJsonObject object, int methodIndex) {
            return object.get(getAccessors()[methodIndex].getName());
        }

        @Override
        public AccessibleJsonObject newInstance() {
            return new AccessibleJsonObject();
        }
    }

    private static final class SimpleParameterizedType implements ParameterizedType {
        private final Type rawType;
        private final Type[] actualTypeArguments;

        private SimpleParameterizedType(Type rawType, Type... actualTypeArguments) {
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments.clone();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }
}
