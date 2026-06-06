/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.parser.deserializer.PropertyProcessable;
import com.alibaba.fastjson.parser.deserializer.PropertyProcessableDeserializer;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class PropertyProcessableDeserializerTest {
    @Test
    void parseObjectInstantiatesAndPopulatesPropertyProcessableType() {
        PropertyProcessableBean bean = JSON.parseObject(
                "{\"name\":\"fastjson\",\"count\":83,\"enabled\":true}", PropertyProcessableBean.class);

        assertThat(bean.values)
                .containsEntry("name", "fastjson")
                .containsEntry("count", 83)
                .containsEntry("enabled", true);
    }

    @Test
    void parserConfigCreatesPropertyProcessableDeserializer() {
        ParserConfig config = new ParserConfig();

        ObjectDeserializer deserializer = config.getDeserializer(PropertyProcessableBean.class);

        assertThat(deserializer).isInstanceOf(PropertyProcessableDeserializer.class);
    }

    public static class PropertyProcessableBean implements PropertyProcessable {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public PropertyProcessableBean() {
        }

        @Override
        public Type getType(String name) {
            if ("count".equals(name)) {
                return Integer.class;
            }
            if ("enabled".equals(name)) {
                return Boolean.class;
            }
            return String.class;
        }

        @Override
        public void apply(String name, Object value) {
            values.put(name, value);
        }
    }
}
