/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.introspect.BasicBeanDescription;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedMethodTest {
    @Test
    void invokesMethodsObtainedThroughJacksonIntrospection() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BasicBeanDescription description = (BasicBeanDescription) mapper
            .getDeserializationConfig()
            .introspect(mapper.constructType(MethodBean.class));
        MethodBean bean = new MethodBean();

        AnnotatedMethod factory = description.findMethod("create", new Class<?>[0]);
        AnnotatedMethod getter = description.findMethod("getValue", new Class<?>[0]);
        AnnotatedMethod setter = description.findMethod("setValue", new Class<?>[] { String.class });

        assertThat(factory.call()).isInstanceOf(MethodBean.class);
        assertThat(factory.call(new Object[0])).isInstanceOf(MethodBean.class);
        setter.setValue(bean, "set");
        assertThat(getter.callOn(bean)).isEqualTo("set");
    }

    public static class MethodBean {
        private String value = "initial";

        public static MethodBean create() {
            return new MethodBean();
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
