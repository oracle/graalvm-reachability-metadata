/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAnySetter;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SettableAnyPropertyTest {
    @Test
    void jsonAnySetterReceivesUnknownProperties() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        AnySetterBean result = mapper.readValue(
                "{\"id\":\"known\",\"enabled\":true,\"count\":3}",
                AnySetterBean.class);

        assertThat(result.id).isEqualTo("known");
        assertThat(result.additionalProperties)
                .containsEntry("enabled", Boolean.TRUE)
                .containsEntry("count", Integer.valueOf(3));
    }

    public static class AnySetterBean {
        public String id;
        public final Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

        @JsonAnySetter
        public void addAdditionalProperty(String name, Object value) {
            additionalProperties.put(name, value);
        }
    }
}
