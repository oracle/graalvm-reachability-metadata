/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webmvc;

import java.util.Locale;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;

import org.springframework.boot.webmvc.autoconfigure.DispatcherServletPath;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.BeanNameViewResolver;

import static org.assertj.core.api.Assertions.assertThat;

public class ErrorMvcAutoConfigurationInnerStaticViewTest {
    @Test
    void rendersAnAutoConfiguredWhitelabelPageWithEscapedErrorDetails() throws Exception {
        try (GenericWebApplicationContext context = new GenericWebApplicationContext(new MockServletContext())) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("errorTest",
                    Map.of("spring.web.error.include-message", "always",
                            "spring.web.error.include-stacktrace", "always")));
            context.registerBean("dispatcherServletPath", DispatcherServletPath.class, () -> () -> "/");
            new AnnotatedBeanDefinitionReader(context).register(ErrorMvcAutoConfiguration.class);
            context.refresh();
            BasicErrorController controller = context.getBean(BasicErrorController.class);
            MockHttpServletRequest request = new MockHttpServletRequest(context.getServletContext());
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/orders");
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("<script>private</script>"));
            MockHttpServletResponse response = new MockHttpServletResponse();

            ModelAndView modelAndView = controller.errorHtml(request, response);
            BeanNameViewResolver resolver = context.getBean(BeanNameViewResolver.class);
            View view = resolver.resolveViewName(modelAndView.getViewName(), Locale.ROOT);
            assertThat(view).isNotNull();
            assertThat(view.getContentType()).isEqualTo("text/html");
            view.render(modelAndView.getModel(), request, response);

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
            assertThat(response.getContentAsString()).contains("Whitelabel Error Page",
                    "type=Internal Server Error, status=500", "&lt;script&gt;private&lt;/script&gt;",
                    "white-space:pre-wrap", "IllegalStateException").doesNotContain("<script>");
        }
    }
}
