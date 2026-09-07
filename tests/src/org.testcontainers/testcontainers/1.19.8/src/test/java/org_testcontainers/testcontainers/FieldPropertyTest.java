/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.deser.impl.FieldProperty;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.introspect.BasicBeanDescription;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldPropertyTest {
    @Test
    void writesValuesThroughJacksonFieldProperties() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BasicBeanDescription description = (BasicBeanDescription) mapper
            .getDeserializationConfig()
            .introspect(mapper.constructType(FieldBean.class));
        BeanPropertyDefinition definition = description.findProperties().get(0);
        FieldProperty property = new FieldProperty(
            definition,
            mapper.constructType(String.class),
            null,
            description.getClassAnnotations(),
            definition.getField()
        );
        property.fixAccess(mapper.getDeserializationConfig());
        FieldBean target = new FieldBean();

        property.set(target, "set");
        assertThat(property.setAndReturn(target, "returned")).isSameAs(target);
        assertThat(target.value).isEqualTo("returned");
    }

    public static class FieldBean {
        public String value;
    }
}
