/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MissingRequiredPropertiesException;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;

/** Verifies configuration resolution against ordered application property sources. */
public class PropertySourcesPropertyResolverTest {
    @Test
    void resolvesNestedPlaceholdersAndConvertsValuesFromTheHighestPrioritySource() {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("defaults", Map.of(
                "service.host", "localhost",
                "service.port", "8080",
                "service.enabled", "true",
                "service.url", "${service.scheme:http}://${service.host}:${service.port}/api")));
        sources.addFirst(new MapPropertySource("application", Map.of(
                "service.host", "service.internal", "service.port", "9090")));
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(sources);

        assertThat(resolver.getRequiredProperty("service.url")).isEqualTo("http://service.internal:9090/api");
        assertThat(resolver.getRequiredProperty("service.port", Integer.class)).isEqualTo(9090);
        assertThat(resolver.getRequiredProperty("service.enabled", Boolean.class)).isTrue();
        assertThat(resolver.getProperty("service.retries", Integer.class, 3)).isEqualTo(3);
        assertThat(resolver.resolveRequiredPlaceholders("Connect to ${service.host}"))
                .isEqualTo("Connect to service.internal");

        sources.remove("application");

        assertThat(resolver.getRequiredProperty("service.url")).isEqualTo("http://localhost:8080/api");
    }

    @Test
    void usesAnApplicationConverterForTypedConfiguration() {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("application", Map.of("service.timeout", "PT30S")));
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(sources);
        resolver.getConversionService().addConverter(String.class, Duration.class, Duration::parse);

        assertThat(resolver.getRequiredProperty("service.timeout", Duration.class)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void validatesRequiredPropertiesBeforeResolvingConfiguration() {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("application", Map.of("service.host", "localhost")));
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(sources);
        resolver.setRequiredProperties("service.host", "service.port");

        MissingRequiredPropertiesException failure = assertThrows(
                MissingRequiredPropertiesException.class, resolver::validateRequiredProperties);

        assertThat(failure.getMissingRequiredProperties()).containsExactly("service.port");
        sources.addFirst(new MapPropertySource("overrides", Map.of("service.port", "8080")));
        resolver.validateRequiredProperties();
        assertThat(resolver.getRequiredProperty("service.port", Integer.class)).isEqualTo(8080);
    }
}
