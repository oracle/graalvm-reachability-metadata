/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyWriterTest {
    @Test
    void serializesBeanPropertyThroughGetterMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(new MethodAccessBean("method-value"));

        assertThat(json).contains("\"description\":\"method-value\"");
    }

    @Test
    void serializesBeanPropertyThroughPublicField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(new FieldAccessBean("field-value"));

        assertThat(json).contains("\"label\":\"field-value\"");
    }

    public static class MethodAccessBean {
        private final String description;

        public MethodAccessBean(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static class FieldAccessBean {
        public final String label;

        public FieldAccessBean(String label) {
            this.label = label;
        }
    }
}
