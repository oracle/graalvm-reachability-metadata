/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.annotation.ExceptionHandlerMappingInfo;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionHandlerMethodResolverTest {

    @Test
    void discoversScopedAdviceAndInvokesMediaTypeSpecificExceptionHandler() throws Exception {
        try (GenericWebApplicationContext context = new GenericWebApplicationContext()) {
            context.registerBean("remoteAdvice", RemoteAdvice.class);
            context.refresh();
            List<ControllerAdviceBean> beans = ControllerAdviceBean.findAnnotatedBeans(context);
            assertThat(beans).hasSize(1);
            ControllerAdviceBean advice = beans.get(0);
            assertThat(advice.isApplicableToBeanType(HandlerMethodTest.Endpoint.class)).isTrue();
            assertThat(advice.isApplicableToBeanType(String.class)).isFalse();

            ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(advice.getBeanType());
            RestClientException failure = new RestClientException("upstream unavailable");
            ExceptionHandlerMappingInfo mapping = resolver.resolveExceptionMapping(failure, MediaType.APPLICATION_JSON);
            assertThat(mapping).isNotNull();
            assertThat(mapping.getProducibleTypes()).containsExactly(MediaType.APPLICATION_JSON);
            InvocableHandlerMethod handler = new InvocableHandlerMethod(advice.resolveBean(), mapping.getHandlerMethod());
            assertThat(handler.invokeForRequest(new ServletWebRequest(new MockHttpServletRequest()),
                    new ModelAndViewContainer(), failure)).isEqualTo("remote: upstream unavailable");
        }
    }

    @RestControllerAdvice(assignableTypes = HandlerMethodTest.Endpoint.class)
    public static final class RemoteAdvice {
        @ExceptionHandler(value = RestClientException.class, produces = "application/json")
        public String remoteFailure(RestClientException failure) {
            return "remote: " + failure.getMessage();
        }
    }
}
