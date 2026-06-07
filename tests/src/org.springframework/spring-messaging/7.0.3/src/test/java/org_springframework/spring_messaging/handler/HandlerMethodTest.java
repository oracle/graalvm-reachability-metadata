/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_messaging.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.HandlerMethod;

public class HandlerMethodTest {
    @Test
    void createsHandlerMethodFromBeanAndPublicMethodName() throws Exception {
        MessageHeaders headers = new MessageHeaders(Map.of("contentType", "text/plain"));

        HandlerMethod handlerMethod = new HandlerMethod(headers, "get", Object.class, Class.class);

        assertThat(handlerMethod.getBean()).isSameAs(headers);
        assertThat(handlerMethod.getBeanType()).isEqualTo(MessageHeaders.class);
        assertThat(handlerMethod.getMethod().getName()).isEqualTo("get");
        assertThat(handlerMethod.getMethod().getParameterTypes()).containsExactly(Object.class, Class.class);
        assertThat(handlerMethod.getShortLogMessage()).isEqualTo("MessageHeaders#get[2 args]");
    }
}
