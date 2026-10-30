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
    void suppliesAnEmptyArrayForAnOmittedDefaultedParameter() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("route.name", "orders")));

        Route route = binder.bind("route", Route.class).get();

        assertThat(route.name()).isEqualTo("orders");
        assertThat(route.destinations()).isNotNull().isEmpty();
    }

    public record Route(String name, @DefaultValue Destination[] destinations) {
    }

    public record Destination(String host) {
    }
}
