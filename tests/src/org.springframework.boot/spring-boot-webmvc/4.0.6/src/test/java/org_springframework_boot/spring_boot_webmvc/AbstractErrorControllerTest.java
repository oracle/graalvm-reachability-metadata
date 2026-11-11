/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webmvc;

import java.util.List;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.InternalResourceView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class AbstractErrorControllerTest {
    @Test
    void dispatchesHtmlErrorsUsingTheHighestPriorityMatchingViewResolver() throws Exception {
        BasicErrorController controller = new BasicErrorController(new DefaultErrorAttributes(), new ErrorProperties(),
                List.of(new OrderedErrorViewResolver(20, "genericError"),
                        new OrderedErrorViewResolver(10, "serviceUnavailable")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setViewResolvers((name, locale) -> new InternalResourceView("/pages/" + name + ".html")).build();

        MvcResult result = mvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 503)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/orders"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(result.getResponse().getForwardedUrl()).isEqualTo("/pages/serviceUnavailable.html");
        ModelAndView view = result.getModelAndView();
        assertThat(view).isNotNull();
        assertThat(view.getViewName()).isEqualTo("serviceUnavailable");
        assertThat(view.getModel()).containsEntry("status", 503).containsEntry("error", "Service Unavailable");
    }

    private static final class OrderedErrorViewResolver implements ErrorViewResolver, Ordered {
        private final int order;
        private final String viewName;

        private OrderedErrorViewResolver(int order, String viewName) {
            this.order = order;
            this.viewName = viewName;
        }

        @Override
        public int getOrder() {
            return this.order;
        }

        @Override
        public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
            return new ModelAndView(this.viewName, model);
        }
    }
}
