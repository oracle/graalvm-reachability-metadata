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

public class ValueObjectBinderTest {
    @Test
    void createsEmptyArrayDefaultsForConstructorBoundConfiguration() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("routing.name", "primary")));
        Routing routing = binder.bind("routing", Routing.class).get();

        assertThat(routing.name()).isEqualTo("primary");
        assertThat(routing.hosts()).isNotNull().isEmpty();
        assertThat(routing.ports()).isNotNull().isEmpty();
    }

    public record Routing(String name, @DefaultValue String[] hosts, @DefaultValue int[] ports) {
    }
}
