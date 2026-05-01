/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyBuilderTest {
    @Test
    void suppressesDefaultGetterValueByReadingDefaultBeanThroughMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String defaultJson = mapper.writeValueAsString(new GetterDefaultsBean());
        String customJson = mapper.writeValueAsString(new GetterDefaultsBean("custom-name"));

        assertThat(defaultJson).doesNotContain("name");
        assertThat(customJson).contains("\"name\":\"custom-name\"");
    }

    @Test
    void suppressesDefaultFieldValueByReadingDefaultBeanThroughField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String defaultJson = mapper.writeValueAsString(new FieldDefaultsBean());
        String customJson = mapper.writeValueAsString(new FieldDefaultsBean("custom-label"));

        assertThat(defaultJson).doesNotContain("label");
        assertThat(customJson).contains("\"label\":\"custom-label\"");
    }

    @JsonSerialize(include = JsonSerialize.Inclusion.NON_DEFAULT)
    public static class GetterDefaultsBean {
        private String name = "standard-name";

        public GetterDefaultsBean() {
        }

        public GetterDefaultsBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @JsonSerialize(include = JsonSerialize.Inclusion.NON_DEFAULT)
    public static class FieldDefaultsBean {
        public String label = "standard-label";

        public FieldDefaultsBean() {
        }

        public FieldDefaultsBean(String label) {
            this.label = label;
        }
    }
}
