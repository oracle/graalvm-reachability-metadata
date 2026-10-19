/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.BeanDescription;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import org.junit.jupiter.api.Test;

public class AnnotatedMethodTest {
    @Test
    void invokesIntrospectedStaticAndInstanceMethods() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription description = mapper.getDeserializationConfig().introspect(
                mapper.constructType(MethodTarget.class));
        AnnotatedMethod create = factoryMethod(description, "create");
        AnnotatedMethod fromString = factoryMethod(description, "fromString");
        AnnotatedMethod getValue = description.findMethod("getValue", new Class<?>[0]);
        AnnotatedMethod setValue = description.findMethod("setValue", new Class<?>[] {String.class});
        AnnotatedMethod combine = description.findMethod(
                "combine", new Class<?>[] {String.class, String.class});

        MethodTarget created = (MethodTarget) create.call();
        MethodTarget supplied = (MethodTarget) fromString.call1("supplied");
        MethodTarget suppliedWithArray = (MethodTarget) fromString.call(new Object[] {"array"});
        setValue.setValue(created, "set");

        assertThat(getValue.getValue(created)).isEqualTo("set");
        assertThat(getValue.callOn(supplied)).isEqualTo("supplied");
        assertThat(getValue.callOn(suppliedWithArray)).isEqualTo("array");
        assertThat(combine.callOnWith(created, "left", "right")).isEqualTo("left-right-set");
    }

    private static AnnotatedMethod factoryMethod(BeanDescription description, String name) {
        for (AnnotatedMethod method : description.getFactoryMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new AssertionError("Missing factory method " + name);
    }

    public static class MethodTarget {
        private String value;

        public MethodTarget() {
            value = "created";
        }

        @JsonCreator
        public static MethodTarget create() {
            return new MethodTarget();
        }

        public static MethodTarget fromString(String value) {
            MethodTarget target = new MethodTarget();
            target.value = value;
            return target;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String combine(String left, String right) {
            return left + "-" + right + "-" + value;
        }
    }
}
