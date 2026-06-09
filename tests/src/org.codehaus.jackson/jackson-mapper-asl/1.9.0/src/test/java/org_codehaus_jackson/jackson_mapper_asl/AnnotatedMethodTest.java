/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.introspect.AnnotatedClass;
import org.codehaus.jackson.map.introspect.AnnotatedMethod;
import org.codehaus.jackson.map.introspect.AnnotationMap;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.junit.jupiter.api.Test;

public class AnnotatedMethodTest {
    private static final AnnotationIntrospector INTROSPECTOR = new JacksonAnnotationIntrospector();
    private static final Class<?>[] STRING_PARAMETER = new Class<?>[] {String.class};

    @Test
    public void invokesNoArgumentStaticFactoryThroughAnnotatedMethod() throws Exception {
        Method method = FactoryTarget.class.getMethod("empty");
        AnnotatedMethod factory = new AnnotatedMethod(method, new AnnotationMap(), null);

        assertThat(factory.call()).isEqualTo(new FactoryTarget("empty"));
    }

    @Test
    public void setsBeanPropertyThroughAnnotatedMethod() {
        AnnotatedClass annotatedClass = AnnotatedClass.construct(MethodTarget.class, INTROSPECTOR, null);
        annotatedClass.resolveMemberMethods(null, true);
        MethodTarget target = new MethodTarget("initial");

        AnnotatedMethod setter = annotatedClass.findMethod("setName", STRING_PARAMETER);
        setter.setValue(target, "updated");

        assertThat(target.getName()).isEqualTo("updated");
    }

    public static final class FactoryTarget {
        private final String value;

        private FactoryTarget(String value) {
            this.value = value;
        }

        public static FactoryTarget empty() {
            return new FactoryTarget("empty");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof FactoryTarget)) {
                return false;
            }
            FactoryTarget that = (FactoryTarget) o;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public static final class MethodTarget {
        private String name;

        public MethodTarget(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
