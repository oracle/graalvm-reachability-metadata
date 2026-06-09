/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.junit.jupiter.api.Test;

public class PropertyBuilderTest {
    @Test
    public void nonDefaultInclusionComparesAccessorPropertyWithDefaultBean() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        MethodBackedNonDefaultValue bean = new MethodBackedNonDefaultValue("custom-method-value");

        String json = mapper.writeValueAsString(bean);

        assertThat(json).isEqualTo("{\"value\":\"custom-method-value\"}");
        assertThat(mapper.writeValueAsString(new MethodBackedNonDefaultValue())).isEqualTo("{}");
    }

    @Test
    public void nonDefaultInclusionComparesFieldPropertyWithDefaultBean() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        FieldBackedNonDefaultValue bean = new FieldBackedNonDefaultValue("custom-field-value");

        String json = mapper.writeValueAsString(bean);

        assertThat(json).isEqualTo("{\"value\":\"custom-field-value\"}");
        assertThat(mapper.writeValueAsString(new FieldBackedNonDefaultValue())).isEqualTo("{}");
    }

    @JsonSerialize(include = JsonSerialize.Inclusion.NON_DEFAULT)
    public static final class MethodBackedNonDefaultValue {
        private String value = "default-method-value";

        public MethodBackedNonDefaultValue() {
        }

        public MethodBackedNonDefaultValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @JsonSerialize(include = JsonSerialize.Inclusion.NON_DEFAULT)
    public static final class FieldBackedNonDefaultValue {
        public String value = "default-field-value";

        public FieldBackedNonDefaultValue() {
        }

        public FieldBackedNonDefaultValue(String value) {
            this.value = value;
        }
    }
}
