/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.introspect.AnnotatedField;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.introspect.BasicBeanDescription;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedFieldTest {
    @Test
    void readsAndWritesAnnotatedFieldsDuringJsonRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BasicBeanDescription description = (BasicBeanDescription) mapper
            .getDeserializationConfig()
            .introspect(mapper.constructType(FieldBean.class));
        BeanPropertyDefinition property = description.findProperties().get(0);
        AnnotatedField field = property.getField();
        FieldBean bean = new FieldBean();

        field.setValue(bean, "set");

        assertThat(field.getValue(bean)).isEqualTo("set");
        assertThat(mapper.writeValueAsString(bean)).isEqualTo("{\"value\":\"set\"}");
    }

    public static class FieldBean {
        public String value;
    }
}
