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
    public void invokesStaticNoArgumentMethod() throws Exception {
        Method method = StaticFactory.class.getDeclaredMethod("defaultName");
        AnnotatedMethod annotatedMethod = new AnnotatedMethod(
                method, new AnnotationMap(), new AnnotationMap[0]);

        Object value = annotatedMethod.call();

        assertThat(value).isEqualTo("default-name");
    }

    @Test
    public void setsBeanValueThroughAnnotatedMethod() {
        AnnotatedClass annotatedClass = AnnotatedClass.construct(
                SetterTarget.class, INTROSPECTOR, null);
        annotatedClass.resolveMemberMethods(null, true);
        AnnotatedMethod setter = annotatedClass.findMethod("setName", STRING_PARAMETER);
        SetterTarget target = new SetterTarget();

        setter.setValue(target, "updated-name");

        assertThat(target.name).isEqualTo("updated-name");
    }

    public static final class StaticFactory {
        public static String defaultName() {
            return "default-name";
        }
    }

    public static final class SetterTarget {
        private String name;

        public void setName(String name) {
            this.name = name;
        }
    }
}
