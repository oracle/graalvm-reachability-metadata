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
import java.util.HashMap;
import net.minidev.json.JSONNavi;
import net.minidev.json.mapper.AMapper;
import net.minidev.json.mapper.Mapper;
import org.junit.jupiter.api.Test;

public class CollectionMapperInnerMapTypeTest {
    @Test
    void parsesObjectIntoParameterizedMapType() {
        ParameterizedType mapType = new SimpleParameterizedType(HashMap.class, String.class, Integer.class);
        AMapper<HashMap<String, Integer>> mapper = Mapper.getMapper(mapType);

        JSONNavi<HashMap<String, Integer>> navi = new JSONNavi<>("{\"one\":1,\"two\":2}", mapper);

        assertThat(navi.getCurrentObject())
                .isInstanceOf(HashMap.class)
                .isEqualTo(expectedMap());
    }

    private static HashMap<String, Integer> expectedMap() {
        HashMap<String, Integer> expected = new HashMap<>();
        expected.put("one", 1);
        expected.put("two", 2);
        return expected;
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
