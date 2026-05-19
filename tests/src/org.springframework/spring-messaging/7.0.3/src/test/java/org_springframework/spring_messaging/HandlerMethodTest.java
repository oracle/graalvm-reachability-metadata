/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.messaging.handler.HandlerMethod;

public class HandlerMethodTest {

    @Test
    void constructsHandlerMethodFromBeanAndPublicMethodName() throws Exception {
        SampleHandler bean = new SampleHandler();

        HandlerMethod handlerMethod = new HandlerMethod(bean, "handle", String.class, Integer.class);

        Method method = handlerMethod.getMethod();
        assertThat(handlerMethod.getBean()).isSameAs(bean);
        assertThat(handlerMethod.getBeanType()).isEqualTo(SampleHandler.class);
        assertThat(method.getName()).isEqualTo("handle");
        assertThat(method.getParameterTypes()).containsExactly(String.class, Integer.class);
        assertThat(handlerMethod.getMethodParameters()).hasSize(2);
    }

    public static final class SampleHandler {

        public String handle(String payload, Integer sequence) {
            return payload + sequence;
        }
    }
}
