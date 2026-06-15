/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class AnyGetterWriterTest {
    @Test
    public void serializesEntriesReturnedByJsonAnyGetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BeanWithAnyGetter bean = new BeanWithAnyGetter("catalog");
        bean.put("enabled", true);
        bean.put("priority", 3);

        String json = mapper.writeValueAsString(bean);

        assertThat(json)
                .contains("\"name\":\"catalog\"")
                .contains("\"enabled\":true")
                .contains("\"priority\":3")
                .doesNotContain("attributes")
                .doesNotContain("anyProperties");
    }

    public static final class BeanWithAnyGetter {
        private final String name;
        private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();

        public BeanWithAnyGetter(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void put(String key, Object value) {
            attributes.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> anyProperties() {
            return attributes;
        }
    }
}
