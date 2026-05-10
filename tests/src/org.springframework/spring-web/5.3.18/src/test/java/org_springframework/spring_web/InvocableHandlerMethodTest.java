/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.web.method.support.InvocableHandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

public class InvocableHandlerMethodTest {
    @Test
    void invokeForRequestCallsHandlerMethod() throws Exception {
        GreetingHandler handler = new GreetingHandler();
        Method method = GreetingHandler.class.getMethod("greet", String.class);
        InvocableHandlerMethod handlerMethod = new InvocableHandlerMethod(handler, method);

        Object result = handlerMethod.invokeForRequest(null, null, "Spring");

        assertThat(result).isEqualTo("Hello Spring");
        assertThat(handler.invocationCount).isEqualTo(1);
    }

    public static class GreetingHandler {
        private int invocationCount;

        public String greet(String name) {
            this.invocationCount++;
            return "Hello " + name;
        }
    }
}
