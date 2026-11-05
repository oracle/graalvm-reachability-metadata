/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webmvc;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.web.WebProperties.Resources;
import org.springframework.boot.webmvc.autoconfigure.error.DefaultErrorViewResolver;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultErrorViewResolverTest {
    @Test
    void rendersTheStatusSpecificPageBeforeTheSeriesPage() throws Exception {
        assertRenderedErrorPage(HttpStatus.NOT_FOUND, "<h1>Order not found</h1>");
    }

    @Test
    void rendersTheClientErrorSeriesPage() throws Exception {
        assertRenderedErrorPage(HttpStatus.BAD_REQUEST, "<h1>Check your order request</h1>");
    }

    @Test
    void rendersTheServerErrorSeriesPage() throws Exception {
        assertRenderedErrorPage(HttpStatus.SERVICE_UNAVAILABLE, "<h1>Please try your order again later</h1>");
    }

    @Test
    void leavesUnmatchedStatusesForOtherViewResolvers() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            DefaultErrorViewResolver resolver = createResolver(context);

            assertThat(resolver.resolveErrorView(new MockHttpServletRequest(), HttpStatus.OK, Map.of())).isNull();
        }
    }

    private void assertRenderedErrorPage(HttpStatus status, String expectedBody) throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            DefaultErrorViewResolver resolver = createResolver(context);
            MockHttpServletRequest request = new MockHttpServletRequest();
            Map<String, Object> model = Map.of("status", status.value(), "path", "/orders");

            ModelAndView modelAndView = resolver.resolveErrorView(request, status, model);

            assertThat(modelAndView).isNotNull();
            assertThat(modelAndView.getModel()).containsAllEntriesOf(model);
            View view = modelAndView.getView();
            assertThat(view).isNotNull();
            MockHttpServletResponse response = new MockHttpServletResponse();
            view.render(modelAndView.getModel(), request, response);

            assertThat(response.getContentType()).isEqualTo("text/html");
            assertThat(response.getContentAsString()).isEqualTo(expectedBody + "\n");
        }
    }

    private DefaultErrorViewResolver createResolver(GenericApplicationContext context) {
        Resources resources = new Resources();
        resources.setStaticLocations(new String[]{"classpath:/webmvc-test-pages/"});
        return new DefaultErrorViewResolver(context, resources);
    }
}
