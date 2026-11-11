/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWarDeployment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DeploymentConditionsTest {
    @Test
    void nonWebApplicationUsesTheNonWarDefault() {
        new ApplicationContextRunner().withUserConfiguration(Features.class).run(context -> {
            assertEquals("embedded", context.getBean("nonWarFeature"));
            assertFalse(context.containsBean("servletFeature"));
        });
    }

    @Test
    void servletApplicationWithoutAnExternalContainerMatchesBothConditions() {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.register(Features.class);
            context.refresh();
            assertEquals("servlet", context.getBean("servletFeature"));
            assertEquals("embedded", context.getBean("nonWarFeature"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Features {
        @Bean
        @ConditionalOnNotWarDeployment
        public String nonWarFeature() {
            return "embedded";
        }

        @Bean
        @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
        public String servletFeature() {
            return "servlet";
        }
    }
}
