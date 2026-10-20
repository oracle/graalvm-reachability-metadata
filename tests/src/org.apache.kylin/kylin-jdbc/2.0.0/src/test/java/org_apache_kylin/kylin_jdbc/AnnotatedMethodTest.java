/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.BeanDescription;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedMethodTest {
    @Test
    void invokesMethodsDiscoveredByJacksonIntrospection() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription description =
                mapper.getSerializationConfig().introspect(mapper.constructType(MethodBean.class));
        BeanPropertyDefinition property = description.findProperties().get(0);
        AnnotatedMethod getter = property.getGetter();
        AnnotatedMethod setter = mapper.getDeserializationConfig()
                .introspect(mapper.constructType(MethodBean.class))
                .findProperties()
                .get(0)
                .getSetter();
        AnnotatedMethod create = null;
        AnnotatedMethod from = null;
        for (AnnotatedMethod factory : description.getFactoryMethods()) {
            if (factory.getName().equals("create")) {
                create = factory;
            } else if (factory.getName().equals("from")) {
                from = factory;
            }
        }
        MethodBean bean = new MethodBean();

        setter.setValue(bean, "north");
        assertThat(getter.getValue(bean)).isEqualTo("north");
        assertThat(create).isNotNull();
        assertThat(from).isNotNull();
        assertThat(create.call()).isEqualTo(new MethodBean("created"));
        assertThat(from.call(new Object[] {"factory"})).isEqualTo(new MethodBean("factory"));
        assertThat(from.call1("single")).isEqualTo(new MethodBean("single"));
    }

    public static class MethodBean {
        private String value;

        public MethodBean() {
        }

        public MethodBean(String value) {
            this.value = value;
        }

        @JsonCreator
        public static MethodBean create() {
            return new MethodBean("created");
        }

        @JsonCreator
        public static MethodBean from(String value) {
            return new MethodBean(value);
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MethodBean && value.equals(((MethodBean) other).value);
        }
    }
}
