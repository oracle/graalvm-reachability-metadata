/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.BeanDescription;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.junit.jupiter.api.Test;

public class AnnotatedMethodTest {

    @Test
    void invokesMethodsDiscoveredByJacksonIntrospection() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final BeanDescription serialization = mapper.getSerializationConfig()
                .introspect(mapper.constructType(MethodBean.class));
        final BeanDescription deserialization = mapper.getDeserializationConfig()
                .introspect(mapper.constructType(MethodBean.class));
        final AnnotatedMethod getter = property(serialization).getGetter();
        final AnnotatedMethod setter = property(deserialization).getSetter();
        final MethodBean bean = new MethodBean();

        setter.callOnWith(bean, "orders");
        assertThat(getter.callOn(bean)).isEqualTo("orders");
        setter.setValue(bean, "events");
        assertThat(getter.getValue(bean)).isEqualTo("events");
    }

    private static BeanPropertyDefinition property(BeanDescription description) {
        return description.findProperties().stream()
                .filter(candidate -> candidate.getName().equals("name"))
                .findFirst()
                .orElseThrow();
    }

    public static final class MethodBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
