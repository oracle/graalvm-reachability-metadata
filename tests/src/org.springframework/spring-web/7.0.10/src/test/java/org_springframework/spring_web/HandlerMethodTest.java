/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.lang.annotation.Annotation;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

public class HandlerMethodTest {

    @Test
    void methodParametersIncludeInterfaceParameterAnnotations() throws Exception {
        HandlerMethod handlerMethod = new HandlerMethod(
                new AccountController(), "handle", String.class);

        Annotation[] annotations = handlerMethod.getMethodParameters()[0].getParameterAnnotations();

        RequestParam requestParam = Arrays.stream(annotations)
                .filter(RequestParam.class::isInstance)
                .map(RequestParam.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(requestParam.value()).isEqualTo("accountId");
        assertThat(requestParam.required()).isTrue();
    }

    private interface AccountControllerContract {

        void handle(@RequestParam("accountId") String accountId);
    }

    private static final class AccountController implements AccountControllerContract {

        @Override
        public void handle(String accountId) {
        }
    }
}
