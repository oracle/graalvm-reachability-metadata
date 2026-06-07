/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_messaging.handler.invocation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.springframework.messaging.support.MessageBuilder;

public class InvocableHandlerMethodTest {
    @Test
    void invokesHandlerMethodWithResolvedArguments() throws Exception {
        GreetingHandler handler = new GreetingHandler();
        InvocableHandlerMethod handlerMethod = new InvocableHandlerMethod(handler, "greet");
        Message<String> message = MessageBuilder.withPayload("unused").build();

        Object result = handlerMethod.invoke(message);

        assertThat(result).isEqualTo("hello");
        assertThat(handler.invocations).isEqualTo(1);
    }

    public static final class GreetingHandler {
        private int invocations;

        public String greet() {
            this.invocations++;
            return "hello";
        }
    }
}
