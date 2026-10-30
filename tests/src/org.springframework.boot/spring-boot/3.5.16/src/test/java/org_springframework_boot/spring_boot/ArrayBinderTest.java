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
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayBinderTest {

    @Test
    void bindsIndexedPropertiesToArray() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("app.hosts[0]", "alpha.example.org");
        properties.put("app.hosts[1]", "beta.example.org");
        properties.put("app.hosts[2]", "gamma.example.org");
        Binder binder = new Binder(new MapConfigurationPropertySource(properties));

        String[] hosts = binder.bind("app.hosts", Bindable.of(String[].class)).get();

        assertThat(hosts).containsExactly("alpha.example.org", "beta.example.org", "gamma.example.org");
    }

    @Test
    void convertsIndexedElementsToArrayComponentType() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("app.ports[0]", "8080");
        properties.put("app.ports[1]", "8443");
        Binder binder = new Binder(new MapConfigurationPropertySource(properties));

        Integer[] ports = binder.bind("app.ports", Bindable.of(Integer[].class)).get();

        assertThat(ports).containsExactly(8080, 8443);
    }
}
