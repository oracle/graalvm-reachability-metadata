/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webmvc;

import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeAttribute;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicErrorControllerTest {
    @Test
    void returnsAnErrorResponseWithoutSensitiveDetails() {
        BasicErrorController controller = new BasicErrorController(new DefaultErrorAttributes(), new ErrorProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/orders/missing");
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalArgumentException("Private details"));

        ResponseEntity<Map<String, Object>> result = controller.error(request);

        assertThat(result.getStatusCode().value()).isEqualTo(404);
        assertThat(result.getBody()).containsEntry("status", 404).containsEntry("error", "Not Found")
                .doesNotContainKeys("exception", "trace", "message").doesNotContainValue("Private details");
    }

    @Test
    void honorsRequestParametersForOptionalErrorDetails() {
        ErrorProperties properties = new ErrorProperties();
        properties.setIncludeMessage(IncludeAttribute.ON_PARAM);
        properties.setIncludeStacktrace(IncludeAttribute.ON_PARAM);
        properties.setIncludePath(IncludeAttribute.ON_PARAM);
        properties.setIncludeException(true);
        BasicErrorController controller = new BasicErrorController(new DefaultErrorAttributes(), properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("message", "true");
        request.setParameter("trace", "true");
        request.setParameter("path", "true");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/orders");
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("Storage unavailable"));

        ResponseEntity<Map<String, Object>> included = controller.error(request);

        assertThat(included.getStatusCode().value()).isEqualTo(500);
        assertThat(included.getBody()).containsEntry("message", "Storage unavailable")
                .containsEntry("path", "/orders").containsEntry("exception", "java.lang.IllegalStateException");
        assertThat(included.getBody().get("trace")).asString().contains("IllegalStateException: Storage unavailable");

        request.setParameter("message", "false");
        request.setParameter("trace", "false");
        request.setParameter("path", "false");

        ResponseEntity<Map<String, Object>> excluded = controller.error(request);

        assertThat(excluded.getStatusCode().value()).isEqualTo(500);
        assertThat(excluded.getBody()).doesNotContainKeys("message", "trace", "path")
                .doesNotContainValue("Storage unavailable");
    }

    @Test
    void returnsAnEmptyBodyForNoContentStatus() {
        BasicErrorController controller = new BasicErrorController(new DefaultErrorAttributes(), new ErrorProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 204);

        ResponseEntity<Map<String, Object>> result = controller.error(request);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
        assertThat(result.getBody()).isNull();
    }

    @Test
    void suppliesTheDefaultHtmlErrorViewAndModel() {
        ErrorProperties properties = new ErrorProperties();
        properties.setIncludePath(IncludeAttribute.ALWAYS);
        BasicErrorController controller = new BasicErrorController(new DefaultErrorAttributes(), properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 403);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView view = controller.errorHtml(request, response);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(view.getViewName()).isEqualTo("error");
        assertThat(view.getModel()).containsEntry("status", 403).containsEntry("error", "Forbidden")
                .containsEntry("path", "/admin").doesNotContainKeys("trace", "exception", "message");
    }
}
