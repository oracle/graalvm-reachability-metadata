/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.reflection.AnnotatedMethod;
import com.sun.jersey.core.reflection.MethodList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodListTest {
    @Test
    public void listsPublicMethodsFromClassAndInterfaces() {
        final MethodList methods = new MethodList(ResourceEndpoint.class);

        assertThat(methodNames(methods))
                .contains("classResource", "interfaceResource", "post")
                .doesNotContain("protectedHelper", "privateHelper", "toString");
        assertThat(methodNames(methods.hasAnnotation(Path.class)))
                .contains("classResource", "interfaceResource")
                .doesNotContain("post");
        assertThat(methodNames(methods.hasMetaAnnotation(HttpMethod.class))).containsExactly("post");
        assertThat(methodNames(methods.nameStartsWith("class").hasNumParams(1).hasReturnType(String.class)))
                .containsExactly("classResource");
    }

    private static List<String> methodNames(MethodList methods) {
        final List<String> names = new ArrayList<>();
        for (AnnotatedMethod method : methods) {
            names.add(method.getMethod().getName());
        }
        return names;
    }

    public interface ResourceContract {
        @Path("/contract")
        String interfaceResource(String value);
    }

    public static class ResourceEndpoint implements ResourceContract {
        @Override
        public String interfaceResource(String value) {
            return value;
        }

        @Path("/class")
        public String classResource(String value) {
            return value;
        }

        @CustomPost
        public void post() {
        }

        protected void protectedHelper() {
        }

        private void privateHelper() {
        }
    }

    @HttpMethod("POST")
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CustomPost {
    }
}
