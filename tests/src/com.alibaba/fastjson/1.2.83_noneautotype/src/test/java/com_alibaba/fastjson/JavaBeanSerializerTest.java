/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.serializer.ValueFilter;
import org.junit.jupiter.api.Test;

public class JavaBeanSerializerTest {
    @Test
    void jsonTypeSerializeFilterIsConstructedAndApplied() {
        PrefixValueFilter.constructed = false;

        String json = JSON.toJSONString(new BeanWithJsonTypeFilter("fastjson"));

        assertThat(PrefixValueFilter.constructed).isTrue();
        assertThat(json).isEqualTo("{\"value\":\"filtered-fastjson\"}");
    }

    @JSONType(serialzeFilters = PrefixValueFilter.class)
    public static class BeanWithJsonTypeFilter {
        public final String value;

        public BeanWithJsonTypeFilter(String value) {
            this.value = value;
        }
    }

    public static class PrefixValueFilter implements ValueFilter {
        static boolean constructed;

        public PrefixValueFilter() {
            constructed = true;
        }

        @Override
        public Object process(Object object, String name, Object value) {
            if ("value".equals(name)) {
                return "filtered-" + value;
            }
            return value;
        }
    }
}
