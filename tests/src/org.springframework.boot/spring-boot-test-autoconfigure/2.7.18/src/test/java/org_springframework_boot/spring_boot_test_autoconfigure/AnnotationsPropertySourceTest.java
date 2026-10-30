/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test_autoconfigure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.properties.AnnotationsPropertySource;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;
import org.springframework.boot.test.autoconfigure.properties.SkipPropertyMapping;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationsPropertySourceTest {

    @Test
    void mapsAttributesToPrefixedPropertiesAndHonorsSkipRules() {
        AnnotationsPropertySource properties = new AnnotationsPropertySource("client", DefaultClient.class);

        assertThat(properties.getName()).isEqualTo("client");
        assertThat(properties.getProperty("sample.client.display-name")).isEqualTo("inventory");
        assertThat(properties.getProperty("sample.client.enabled")).isEqualTo(true);
        assertThat(properties.getProperty("sample.client.labels[0]")).isEqualTo("primary");
        assertThat(properties.getProperty("sample.client.labels[1]")).isEqualTo("read-only");
        assertThat(properties.containsProperty("sample.client.secret")).isFalse();
        assertThat(properties.containsProperty("sample.client.timeout")).isFalse();
        assertThat(properties.getPropertyNames()).containsExactlyInAnyOrder(
                "sample.client.display-name", "sample.client.enabled",
                "sample.client.labels[0]", "sample.client.labels[1]");
        assertThat(properties.isEmpty()).isFalse();
    }

    @Test
    void mapsNonDefaultAttributeValues() {
        AnnotationsPropertySource properties = new AnnotationsPropertySource(CustomClient.class);

        assertThat(properties.getProperty("sample.client.display-name")).isEqualTo("billing");
        assertThat(properties.getProperty("sample.client.timeout")).isEqualTo(45);
        assertThat(properties.getProperty("sample.client.enabled")).isEqualTo(false);
        assertThat(properties.containsProperty("sample.client.secret")).isFalse();
    }

    @Test
    void flattensNestedAnnotationsAndTheirArraysIntoIndexedProperties() {
        AnnotationsPropertySource properties = new AnnotationsPropertySource(EndpointClient.class);

        assertThat(properties.getProperty("sample.endpoints.primary.host")).isEqualTo("main.example");
        assertThat(properties.getProperty("sample.endpoints.primary.ports[0]")).isEqualTo(8080);
        assertThat(properties.getProperty("sample.endpoints.primary.ports[1]")).isEqualTo(8443);
        assertThat(properties.getProperty("sample.endpoints.replicas[0].host")).isEqualTo("east.example");
        assertThat(properties.getProperty("sample.endpoints.replicas[0].ports[0]")).isEqualTo(9080);
        assertThat(properties.getProperty("sample.endpoints.replicas[1].host")).isEqualTo("west.example");
        assertThat(properties.getProperty("sample.endpoints.replicas[1].ports[0]")).isEqualTo(9443);
        assertThat(properties.getPropertyNames()).containsExactlyInAnyOrder(
                "sample.endpoints.primary.host", "sample.endpoints.primary.ports[0]",
                "sample.endpoints.primary.ports[1]", "sample.endpoints.replicas[0].host",
                "sample.endpoints.replicas[0].ports[0]", "sample.endpoints.replicas[1].host",
                "sample.endpoints.replicas[1].ports[0]");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @PropertyMapping("sample.client")
    public @interface ClientSettings {
        String displayName() default "inventory";

        boolean enabled() default true;

        String[] labels() default {"primary", "read-only"};

        @PropertyMapping(skip = SkipPropertyMapping.YES)
        String secret() default "not-exported";

        @PropertyMapping(value = "timeout", skip = SkipPropertyMapping.ON_DEFAULT_VALUE)
        int timeoutSeconds() default 30;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @PropertyMapping("sample.endpoints")
    public @interface ClientEndpoints {
        Endpoint primary();

        Endpoint[] replicas();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    public @interface Endpoint {
        @PropertyMapping("host")
        String hostName();

        int[] ports();
    }

    @ClientSettings
    public static class DefaultClient {
    }

    @ClientSettings(displayName = "billing", enabled = false, timeoutSeconds = 45)
    public static class CustomClient {
    }

    @ClientEndpoints(primary = @Endpoint(hostName = "main.example", ports = {8080, 8443}), replicas = {
            @Endpoint(hostName = "east.example", ports = 9080),
            @Endpoint(hostName = "west.example", ports = 9443)
    })
    public static class EndpointClient {
    }
}
