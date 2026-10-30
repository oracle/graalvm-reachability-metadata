/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultBindConstructorProviderInnerConstructorsTest {

    @Test
    void bindsRecordConstructorAndItsDefaultArgument() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "service.host", "gateway.example", "service.port", "8443")));

        Endpoint endpoint = binder.bind("service", Endpoint.class).get();

        assertThat(endpoint.host()).isEqualTo("gateway.example");
        assertThat(endpoint.port()).isEqualTo(8443);
        assertThat(endpoint.protocol()).isEqualTo("https");
    }

    public record Endpoint(String host, int port, @DefaultValue("https") String protocol) {
    }
}
