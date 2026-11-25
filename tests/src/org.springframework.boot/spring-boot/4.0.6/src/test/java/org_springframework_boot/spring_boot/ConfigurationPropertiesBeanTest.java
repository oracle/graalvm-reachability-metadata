/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationPropertiesBeanTest {
    @Test
    // getAnnotation() is Spring Boot's bean accessor, not AnnotatedElement reflection.
    @SuppressWarnings("checkstyle:annotationAccess")
    void bindsAndDescribesAComposedConfigurationPropertiesBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("deployment", Map.of(
                    "orders.remote-host", "warehouse.internal", "orders.workers", "6")));
            context.register(OrdersConfiguration.class);
            context.refresh();

            OrdersSettings settings = context.getBean(OrdersSettings.class);
            assertThat(settings.host()).isEqualTo("warehouse.internal");
            assertThat(settings.workers()).isEqualTo(6);
            assertThat(ConfigurationPropertiesBean.getAll(context).values()).singleElement().satisfies(bean -> {
                assertThat(bean.getInstance()).isSameAs(settings);
                assertThat(bean.getAnnotation().prefix()).isEqualTo("orders");
                assertThat(bean.getAnnotation().value()).isEqualTo("orders");
                assertThat(bean.asBindTarget().getType().resolve()).isEqualTo(OrdersSettings.class);
            });
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OrdersSettings.class)
    public static class OrdersConfiguration {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @ConfigurationProperties
    public @interface ServiceProperties {
        @AliasFor(annotation = ConfigurationProperties.class, attribute = "prefix")
        String value();
    }

    @ConfigurationPropertiesSource
    @ServiceProperties("orders")
    public record OrdersSettings(@Name("remote-host") String host, int workers) {
    }
}
