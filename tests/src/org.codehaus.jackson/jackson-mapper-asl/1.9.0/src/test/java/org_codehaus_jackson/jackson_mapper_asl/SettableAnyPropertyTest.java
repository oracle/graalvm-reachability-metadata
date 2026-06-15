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

import org.codehaus.jackson.annotate.JsonAnySetter;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class SettableAnyPropertyTest {
    @Test
    public void deserializesUnknownPropertiesThroughAnySetterMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        AnySetterBean bean = mapper.readValue("""
                {
                  "name": "known",
                  "extraText": "value",
                  "extraNumber": 42,
                  "extraBoolean": true
                }
                """, AnySetterBean.class);

        assertThat(bean.name).isEqualTo("known");
        assertThat(bean.values)
                .containsEntry("extraText", "value")
                .containsEntry("extraNumber", 42)
                .containsEntry("extraBoolean", true);
    }

    public static final class AnySetterBean {
        public String name;
        private final Map<String, Object> values = new LinkedHashMap<String, Object>();

        @JsonAnySetter
        public void putUnknownValue(String propertyName, Object value) {
            values.put(propertyName, value);
        }
    }
}
