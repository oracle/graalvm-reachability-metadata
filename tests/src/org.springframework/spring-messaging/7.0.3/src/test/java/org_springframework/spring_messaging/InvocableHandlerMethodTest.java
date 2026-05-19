/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.springframework.messaging.support.MessageBuilder;

public class InvocableHandlerMethodTest {

    @Test
    void invokeCallsHandlerMethodWithProvidedArguments() throws Exception {
        GreetingHandler handler = new GreetingHandler();
        InvocableHandlerMethod handlerMethod = new InvocableHandlerMethod(
                handler, "handle", String.class, Integer.class);
        Message<String> message = MessageBuilder.withPayload("unused").build();

        Object result = handlerMethod.invoke(message, "Spring", 7);

        assertThat(result).isEqualTo("Spring-7");
        assertThat(handler.invocations()).isEqualTo(1);
    }

    public static final class GreetingHandler {

        private int invocations;

        public String handle(String name, Integer sequence) {
            this.invocations++;
            return name + "-" + sequence;
        }

        int invocations() {
            return this.invocations;
        }
    }
}
