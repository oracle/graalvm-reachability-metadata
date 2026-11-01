/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webmvc;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeAttribute;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class BasicErrorControllerTest {
    @Test
    void dispatchesAnErrorAsJsonWithoutSensitiveDetails() throws Exception {
        BasicErrorController controller = new BasicErrorController(new DefaultErrorAttributes(), new ErrorProperties());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        MvcResult result = mvc.perform(get("/error").accept(MediaType.APPLICATION_JSON)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/orders/missing")
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalArgumentException("Private details")))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":404", "\"error\":\"Not Found\"")
                .doesNotContain("Private details", "\"exception\"", "\"trace\"", "\"message\"");
    }

    @Test
    void honorsRequestParametersForOptionalErrorDetails() throws Exception {
        ErrorProperties properties = new ErrorProperties();
        properties.setIncludeMessage(IncludeAttribute.ON_PARAM);
        properties.setIncludeStacktrace(IncludeAttribute.ON_PARAM);
        properties.setIncludePath(IncludeAttribute.ON_PARAM);
        properties.setIncludeException(true);
        BasicErrorController controller = new BasicErrorController(new DefaultErrorAttributes(), properties);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        MvcResult included = mvc.perform(get("/error").accept(MediaType.APPLICATION_JSON)
                .param("message", "true").param("trace", "true").param("path", "true")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/orders")
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("Storage unavailable")))
                .andReturn();

        assertThat(included.getResponse().getStatus()).isEqualTo(500);
        assertThat(included.getResponse().getContentAsString()).contains(
                "\"message\":\"Storage unavailable\"", "\"trace\":", "\"path\":\"/orders\"",
                "\"exception\":\"java.lang.IllegalStateException\"");

        MvcResult excluded = mvc.perform(get("/error").accept(MediaType.APPLICATION_JSON)
                .param("message", "false").param("trace", "false").param("path", "false")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/orders")
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("Storage unavailable")))
                .andReturn();

        assertThat(excluded.getResponse().getStatus()).isEqualTo(500);
        assertThat(excluded.getResponse().getContentAsString()).doesNotContain(
                "\"message\"", "\"trace\"", "\"path\"", "Storage unavailable");
    }

    @Test
    void returnsAnEmptyBodyForNoContentStatus() throws Exception {
        BasicErrorController controller = new BasicErrorController(new DefaultErrorAttributes(), new ErrorProperties());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        MvcResult result = mvc.perform(get("/error").accept(MediaType.APPLICATION_JSON)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 204)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
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
