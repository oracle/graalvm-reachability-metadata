/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingFilterBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BeanConditionsTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Features.class));

    @Test
    void beanConditionRequiresAnExistingDependency() {
        runner.withBean(StringBuilder.class, () -> new StringBuilder("orders")).run(context ->
                assertEquals("orders ready", context.getBean("dependent")));
        runner.run(context -> assertFalse(context.containsBean("dependent")));
    }

    @Test
    void singleCandidateConditionResolvesThePrimaryBeanButRejectsAmbiguity() {
        runner.withBean("first", StringBuilder.class, () -> new StringBuilder("first"))
                .withBean("second", StringBuilder.class, () -> new StringBuilder("second"))
                .run(context -> assertFalse(context.containsBean("singleCandidate")));
        runner.withBean("first", StringBuilder.class, () -> new StringBuilder("primary"),
                        definition -> definition.setPrimary(true))
                .withBean("second", StringBuilder.class, () -> new StringBuilder("secondary"))
                .run(context -> assertEquals("primary", context.getBean("singleCandidate")));
        runner.withBean(StringBuilder.class, () -> new StringBuilder("only"))
                .run(context -> assertEquals("only", context.getBean("singleCandidate")));
    }

    @Test
    void defaultFilterBacksOffForDirectAndRegisteredUserFilters() {
        runner.run(context -> assertEquals(1, context.getBeansOfType(RequestFilter.class).size()));
        RequestFilter userFilter = new RequestFilter();
        runner.withBean("userFilter", RequestFilter.class, () -> userFilter).run(context -> {
            assertFalse(context.containsBean("defaultFilter"));
            assertSame(userFilter, context.getBean(RequestFilter.class));
        });
        runner.withUserConfiguration(RegisteredFilter.class).run(context -> {
            assertFalse(context.containsBean("defaultFilter"));
            assertEquals(1, context.getBeansOfType(FilterRegistrationBean.class).size());
        });
    }

    @AutoConfiguration
    public static class Features {
        @Bean
        @ConditionalOnBean(StringBuilder.class)
        public String dependent(List<StringBuilder> inputs) {
            return inputs.stream().map(StringBuilder::toString).collect(Collectors.joining(",")) + " ready";
        }

        @Bean
        @ConditionalOnSingleCandidate(StringBuilder.class)
        public String singleCandidate(StringBuilder input) {
            return input.toString();
        }

        @Bean
        @ConditionalOnMissingFilterBean(RequestFilter.class)
        public RequestFilter defaultFilter() {
            return new RequestFilter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class RegisteredFilter {
        @Bean
        public FilterRegistrationBean<RequestFilter> userFilterRegistration() {
            FilterRegistrationBean<RequestFilter> registration = new FilterRegistrationBean<>(new RequestFilter());
            registration.setName("user-filter");
            return registration;
        }
    }

    public static class RequestFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);
        }
    }
}
