/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnyGetterWriterTest {
    @Test
    void serializesPropertiesReturnedByJsonAnyGetterMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DynamicPropertiesBean bean = new DynamicPropertiesBean("record-one");
        bean.add("enabled", true);
        bean.add("count", 3);

        String json = mapper.writeValueAsString(bean);

        assertThat(json)
                .contains("\"name\":\"record-one\"")
                .contains("\"enabled\":true")
                .contains("\"count\":3");
    }

    public static class DynamicPropertiesBean {
        private final String name;
        private final Map<String, Object> dynamicProperties = new LinkedHashMap<>();

        public DynamicPropertiesBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void add(String key, Object value) {
            dynamicProperties.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getDynamicProperties() {
            return dynamicProperties;
        }
    }
}
