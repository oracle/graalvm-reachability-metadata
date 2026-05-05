/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class BeanPropertyWriterTest {
    @Test
    public void serializesBeanPropertyThroughAccessorMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AccessorBackedProperty bean = new AccessorBackedProperty("method-value");

        String json = mapper.writeValueAsString(bean);

        assertThat(json).isEqualTo("{\"methodValue\":\"method-value\"}");
    }

    @Test
    public void serializesBeanPropertyThroughField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        FieldBackedProperty bean = new FieldBackedProperty("field-value");

        String json = mapper.writeValueAsString(bean);

        assertThat(json).isEqualTo("{\"fieldValue\":\"field-value\"}");
    }

    public static final class AccessorBackedProperty {
        private final String methodValue;

        public AccessorBackedProperty(String methodValue) {
            this.methodValue = methodValue;
        }

        public String getMethodValue() {
            return methodValue;
        }
    }

    public static final class FieldBackedProperty {
        public final String fieldValue;

        public FieldBackedProperty(String fieldValue) {
            this.fieldValue = fieldValue;
        }
    }
}
