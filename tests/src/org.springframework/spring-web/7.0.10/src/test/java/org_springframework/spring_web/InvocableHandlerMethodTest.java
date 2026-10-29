/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.assertj.core.api.Assertions.assertThat;

public class InvocableHandlerMethodTest {

    @Test
    void invokeForRequestCallsHandlerMethodWithoutArguments() throws Exception {
        GreetingController controller = new GreetingController();
        InvocableHandlerMethod handlerMethod = new InvocableHandlerMethod(controller, "greet");
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

        Object returnValue = handlerMethod.invokeForRequest(request, new ModelAndViewContainer());

        assertThat(returnValue).isEqualTo("Hello Spring Web");
        assertThat(controller.invocations()).isEqualTo(1);
    }

    public static final class GreetingController {

        private int invocations;

        public String greet() {
            this.invocations++;
            return "Hello Spring Web";
        }

        int invocations() {
            return this.invocations;
        }
    }
}
