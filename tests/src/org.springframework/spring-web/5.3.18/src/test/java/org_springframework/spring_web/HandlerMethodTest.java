/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

public class HandlerMethodTest {
    @Test
    void mergesParameterAnnotationsDeclaredOnImplementedInterface() throws Exception {
        HandlerImplementation handler = new HandlerImplementation();
        Method method = HandlerImplementation.class.getMethod("handle", String.class);
        HandlerMethod handlerMethod = new HandlerMethod(handler, method);

        Annotation[] annotations = handlerMethod.getMethodParameters()[0].getParameterAnnotations();

        assertThat(annotations).anySatisfy(annotation -> {
            assertThat(annotation).isInstanceOf(RequestParam.class);
            RequestParam requestParam = (RequestParam) annotation;
            assertThat(requestParam.value()).isEqualTo("name");
        });
    }

    public interface HandlerContract {
        String handle(@RequestParam("name") String name);
    }

    public static class HandlerImplementation implements HandlerContract {
        @Override
        public String handle(String name) {
            return "Hello " + name;
        }
    }
}
