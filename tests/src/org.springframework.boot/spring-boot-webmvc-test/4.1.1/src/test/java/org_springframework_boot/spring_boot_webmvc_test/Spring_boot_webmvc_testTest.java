/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webmvc_test;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class Spring_boot_webmvc_testTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class,
                    MockMvcAutoConfiguration.class))
            .withUserConfiguration(MvcConfiguration.class)
            .withPropertyValues("spring.test.mockmvc.print=none");

    @Test
    void autoConfigurationBuildsMockMvcAndAppliesRegisteredFilters() {
        this.contextRunner.run((context) -> {
            assertThat(context).hasSingleBean(MockMvc.class);
            MockMvc mockMvc = context.getBean(MockMvc.class);

            mockMvc.perform(get("/greeting").param("name", "Ada"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Hello, Ada!"))
                    .andExpect(header().string("X-Test-Filter", "applied"));
        });
    }

    @Test
    void autoConfigurationProvidesMockMvcTesterForAssertJAssertions() {
        this.contextRunner.run((context) -> {
            assertThat(context).hasSingleBean(MockMvcTester.class);
            MockMvcTester mockMvcTester = context.getBean(MockMvcTester.class);

            assertThat(mockMvcTester.get().uri("/greeting").param("name", "Spring"))
                    .hasStatusOk()
                    .hasBodyTextEqualTo("Hello, Spring!");
        });
    }

    @Test
    void autoConfigurationCanDisableRegisteredFilters() {
        this.contextRunner.withPropertyValues("spring.test.mockmvc.add-filters=false").run((context) -> {
            MockMvc mockMvc = context.getBean(MockMvc.class);

            mockMvc.perform(get("/greeting"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Hello, World!"))
                    .andExpect(header().doesNotExist("X-Test-Filter"));
        });
    }

    @Test
    void customMockMvcBuilderCustomizerAppliesDefaultRequestValues() {
        this.contextRunner.withUserConfiguration(MockMvcBuilderConfiguration.class).run((context) -> {
            MockMvc mockMvc = context.getBean(MockMvc.class);

            mockMvc.perform(get("/customized-greeting"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("default-value"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class MockMvcBuilderConfiguration {

        @Bean
        MockMvcBuilderCustomizer defaultRequestValue() {
            return (builder) -> builder.defaultRequest(get("/customized-greeting")
                    .header("X-Default-Value", "default-value"));
        }

        @Bean
        CustomizerController customizerController() {
            return new CustomizerController();
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class MvcConfiguration {

        @Bean
        GreetingController greetingController() {
            return new GreetingController();
        }

        @Bean
        FilterRegistrationBean<Filter> responseHeaderFilter() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
            registration.setFilter((request, response, chain) -> {
                ((HttpServletResponse) response).setHeader("X-Test-Filter", "applied");
                chain.doFilter(request, response);
            });
            registration.addUrlPatterns("/*");
            return registration;
        }

    }

    @RestController
    static class GreetingController {

        @GetMapping("/greeting")
        public String greeting(@RequestParam(name = "name", defaultValue = "World") String name) {
            return "Hello, " + name + "!";
        }

    }

    @RestController
    static class CustomizerController {

        @GetMapping("/customized-greeting")
        public String greeting(@RequestHeader("X-Default-Value") String value) {
            return value;
        }

    }

}
