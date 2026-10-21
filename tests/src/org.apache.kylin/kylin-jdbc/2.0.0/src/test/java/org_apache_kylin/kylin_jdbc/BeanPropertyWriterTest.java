/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.BeanDescription;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyWriterTest {
    @Test
    void serializesPropertiesBackedByAFieldAndAMethod() throws Exception {
        PropertyBean bean = new PropertyBean();
        bean.code = 7;
        bean.setLabel("ready");

        String json = new ObjectMapper().writeValueAsString(bean);

        assertThat(json).contains("\"code\":7", "\"label\":\"ready\"");
    }

    @Test
    void serializesFieldAndMethodPropertiesAsArrayElements() throws Exception {
        ArrayBean bean = new ArrayBean();
        bean.code = 11;
        bean.setLabel("array");

        assertThat(new ObjectMapper().writeValueAsString(bean)).isEqualTo("[11,\"array\"]");
    }

    @Test
    void readsIntrospectedFieldAndMethodProperties() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription description =
                mapper.getSerializationConfig().introspect(mapper.constructType(PropertyBean.class));
        PropertyBean bean = new PropertyBean();
        bean.code = 31;
        bean.setLabel("introspected");

        for (BeanPropertyDefinition definition : description.findProperties()) {
            BeanPropertyWriter writer = new BeanPropertyWriter(
                    definition,
                    definition.getAccessor(),
                    description.getClassAnnotations(),
                    mapper.constructType(definition.getAccessor().getGenericType()),
                    null,
                    null,
                    null,
                    false,
                    null);
            if (definition.getName().equals("code")) {
                assertThat(writer.get(bean)).isEqualTo(31);
            } else if (definition.getName().equals("label")) {
                assertThat(writer.get(bean)).isEqualTo("introspected");
            }
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"code", "label"})
    public static class ArrayBean {
        public int code;
        private String label;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    public static class PropertyBean {
        public int code;
        private String label;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
