/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultBindConstructorProviderTest {

    @Test
    void bindsImmutableValueObjectThroughItsOnlyConstructor() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("app.endpoint.host", "api.example.org");
        properties.put("app.endpoint.port", "9443");
        properties.put("app.endpoint.credentials.user", "service");
        Binder binder = new Binder(new MapConfigurationPropertySource(properties));

        Endpoint endpoint = binder.bind("app.endpoint", Bindable.of(Endpoint.class)).get();

        assertThat(endpoint.host()).isEqualTo("api.example.org");
        assertThat(endpoint.port()).isEqualTo(9443);
        assertThat(endpoint.protocol()).isEqualTo("https");
        assertThat(endpoint.credentials().user()).isEqualTo("service");
    }

    public record Endpoint(String host, int port, @DefaultValue("https") String protocol, Credentials credentials) {
    }

    public record Credentials(String user) {
    }
}
