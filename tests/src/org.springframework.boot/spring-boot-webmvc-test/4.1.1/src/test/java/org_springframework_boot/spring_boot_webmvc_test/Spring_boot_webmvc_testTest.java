/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webmvc_test;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletPath;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.boot.webmvc.test.autoconfigure.SpringBootMockMvcBuilderCustomizer;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = Spring_boot_webmvc_testTest.GreetingController.class,
        properties = {"spring.test.mockmvc.add-filters=true", "spring.test.mockmvc.print=none"})
@ContextConfiguration(
        classes = {
            Spring_boot_webmvc_testTest.TestApplication.class,
            Spring_boot_webmvc_testTest.GreetingController.class
        })
@Import(Spring_boot_webmvc_testTest.MockMvcCustomizationConfiguration.class)
public class Spring_boot_webmvc_testTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private DispatcherServlet dispatcherServlet;

    @Autowired private DispatcherServletPath dispatcherServletPath;

    @Autowired private SpringBootMockMvcBuilderCustomizer springBootCustomizer;

    @Test
    void webMvcSliceBuildsMockMvcAndAppliesBuilderAndFilterCustomizers() throws Exception {
        this.mockMvc
                .perform(get("/greeting"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello from-customizer"))
                .andExpect(header().string("X-Request-Filtered", "true"));

        assertThat(this.dispatcherServlet).isSameAs(this.mockMvc.getDispatcherServlet());
        assertThat(this.dispatcherServletPath.getPath()).isEqualTo("/");
    }

    @Test
    void webMvcSliceBindsMockMvcProperties() {
        assertThat(this.springBootCustomizer.isAddFilters()).isTrue();
        assertThat(this.springBootCustomizer.getPrint()).isEqualTo(MockMvcPrint.NONE);
        assertThat(this.springBootCustomizer.isPrintOnlyOnFailure()).isTrue();
    }

    @Controller
    public static class GreetingController {

        @GetMapping("/greeting")
        @ResponseBody
        public String greeting(@RequestHeader("X-Builder-Customized") String customization) {
            return "Hello " + customization;
        }
    }

    @SpringBootConfiguration
    public static class TestApplication {}

    @TestConfiguration(proxyBeanMethods = false)
    public static class MockMvcCustomizationConfiguration {

        @Bean
        MockMvcBuilderCustomizer requestHeaderCustomizer() {
            return new RequestHeaderMockMvcBuilderCustomizer();
        }

        @Bean
        RequestMarkerFilter requestMarkerFilter() {
            return new RequestMarkerFilter();
        }
    }

    public static class RequestHeaderMockMvcBuilderCustomizer implements MockMvcBuilderCustomizer {

        @Override
        public void customize(ConfigurableMockMvcBuilder<?> builder) {
            builder.defaultRequest(get("/").header("X-Builder-Customized", "from-customizer"));
        }
    }

    public static class RequestMarkerFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            response.addHeader("X-Request-Filtered", "true");
            filterChain.doFilter(request, response);
        }
    }
}
