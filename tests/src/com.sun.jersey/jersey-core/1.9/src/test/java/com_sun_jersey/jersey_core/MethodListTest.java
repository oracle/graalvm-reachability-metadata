/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static java.lang.annotation.ElementType.METHOD;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.core.reflection.AnnotatedMethod;
import com.sun.jersey.core.reflection.MethodList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MethodListTest {
    @Test
    void discoversPublicMethodsFromClassConstructor() {
        final MethodList methods = new MethodList(PublicResource.class);
        final List<String> names = methodNames(methods);

        assertThat(names)
                .contains("getValue", "accept", "inheritedValue")
                .doesNotContain("privateValue", "toString", "wait", "getClass");
        final MethodList getters = methods.nameStartsWith("get").hasNumParams(0).hasReturnType(String.class);
        assertThat(methodNames(getters)).containsExactly("getValue");
        assertThat(methodNames(methods.hasAnnotation(ResourceMethod.class)))
                .containsExactly("getValue");
        assertThat(methodNames(methods.hasNotAnnotation(ResourceMethod.class)))
                .contains("accept", "inheritedValue")
                .doesNotContain("getValue");
    }

    private static List<String> methodNames(MethodList methods) {
        final List<String> names = new ArrayList<String>();
        for (final AnnotatedMethod method : methods) {
            names.add(method.getMethod().getName());
        }
        return names;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(METHOD)
    public @interface ResourceMethod {
    }

    public static class ParentResource {
        public int inheritedValue() {
            return 7;
        }
    }

    public static class PublicResource extends ParentResource {
        @ResourceMethod
        public String getValue() {
            return "value";
        }

        public void accept(String value) {
        }

        private String privateValue() {
            return "hidden";
        }
    }
}
