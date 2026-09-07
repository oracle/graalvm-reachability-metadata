/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.deser.impl.MethodProperty;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.introspect.BasicBeanDescription;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodPropertyTest {
    @Test
    void writesValuesThroughJacksonMethodProperties() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BasicBeanDescription description = (BasicBeanDescription) mapper
            .getDeserializationConfig()
            .introspect(mapper.constructType(MethodBean.class));
        BeanPropertyDefinition definition = description.findProperties().get(0);
        MethodProperty property = new MethodProperty(
            definition,
            mapper.constructType(String.class),
            null,
            description.getClassAnnotations(),
            definition.getSetter()
        );
        property.fixAccess(mapper.getDeserializationConfig());
        MethodBean target = new MethodBean();

        property.set(target, "set");
        assertThat(property.setAndReturn(target, "returned")).isSameAs(target);
        assertThat(target.getValue()).isEqualTo("returned");
    }

    public static class MethodBean {
        private String value;

        public String getValue() {
            return value;
        }

        public MethodBean setValue(String value) {
            this.value = value;
            return this;
        }
    }
}
