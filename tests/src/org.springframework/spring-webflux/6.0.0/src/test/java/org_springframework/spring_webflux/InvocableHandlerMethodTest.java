/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webflux;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.result.method.InvocableHandlerMethod;
import org.springframework.web.server.ServerWebExchange;

public class InvocableHandlerMethodTest {
    @Test
    void invokeCallsUnderlyingHandlerMethod() throws Exception {
        GreetingHandler handler = new GreetingHandler();
        Method method = GreetingHandler.class.getMethod("greeting");
        InvocableHandlerMethod invocableMethod = new InvocableHandlerMethod(handler, method);
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/greeting"));

        HandlerResult result = invocableMethod.invoke(exchange, new BindingContext()).block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.getHandler()).isSameAs(invocableMethod);
        assertThat(result.getReturnValue()).isEqualTo("hello from handler");
    }

    public static final class GreetingHandler {
        public String greeting() {
            return "hello from handler";
        }
    }
}
