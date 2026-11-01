/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webmvc;

import java.util.Date;
import java.util.List;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultErrorAttributesTest {
    @Test
    void exposesRootCauseAndRequestDetailsWhenIncluded() {
        DefaultErrorAttributes attributes = new DefaultErrorAttributes();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/orders/42");
        IllegalStateException cause = new IllegalStateException("Order storage unavailable");
        ServletException exception = new ServletException("Request failed", cause);

        assertThat(attributes.resolveException(request, new MockHttpServletResponse(), null, exception)).isNull();
        ServletWebRequest webRequest = new ServletWebRequest(request);
        Map<String, Object> model = attributes.getErrorAttributes(webRequest,
                ErrorAttributeOptions.defaults().including(
                        Include.EXCEPTION, Include.MESSAGE, Include.STACK_TRACE, Include.PATH));

        assertThat(attributes.getError(webRequest)).isSameAs(exception);
        assertThat(model).containsEntry("status", 500)
                .containsEntry("error", "Internal Server Error")
                .containsEntry("path", "/orders/42")
                .containsEntry("message", "Order storage unavailable")
                .containsEntry("exception", IllegalStateException.class.getName());
        assertThat(model.get("timestamp")).isInstanceOf(Date.class);
        assertThat((String) model.get("trace")).contains("IllegalStateException: Order storage unavailable");
    }

    @Test
    void excludesSensitiveDetailsByDefault() {
        DefaultErrorAttributes attributes = new DefaultErrorAttributes();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/orders");
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalArgumentException("Private details"));

        Map<String, Object> model = attributes.getErrorAttributes(new ServletWebRequest(request),
                ErrorAttributeOptions.defaults());

        assertThat(model).containsEntry("status", 400).containsEntry("error", "Bad Request");
        assertThat(model).doesNotContainKeys("exception", "trace", "message", "errors");
    }

    @Test
    void includesFieldValidationErrorsWhenRequested() {
        MapBindingResult bindingResult = new MapBindingResult(Map.of("quantity", -1), "order");
        FieldError fieldError = new FieldError("order", "quantity", -1, false,
                new String[]{"Positive"}, null, "Quantity must be positive");
        bindingResult.addError(fieldError);
        BindException exception = new BindException(bindingResult);
        DefaultErrorAttributes attributes = new DefaultErrorAttributes();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
        attributes.resolveException(request, new MockHttpServletResponse(), null, exception);

        Map<String, Object> model = attributes.getErrorAttributes(new ServletWebRequest(request),
                ErrorAttributeOptions.defaults().including(Include.BINDING_ERRORS, Include.MESSAGE));

        assertThat(model).containsEntry("message", "Validation failed for object='order'. Error count: 1");
        assertThat((List<?>) model.get("errors")).hasSize(1);
        assertThat(model.get("errors")).isEqualTo(List.of(fieldError));
    }

    @Test
    void prefersTheServletErrorMessageOverTheExceptionMessage() {
        DefaultErrorAttributes attributes = new DefaultErrorAttributes();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "Order not found");
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalArgumentException("Unknown identifier"));

        Map<String, Object> model = attributes.getErrorAttributes(new ServletWebRequest(request),
                ErrorAttributeOptions.defaults().including(Include.MESSAGE));

        assertThat(model).containsEntry("message", "Order not found").containsEntry("error", "Not Found");
    }
}
