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
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayBinderTest {
    @Test
    void bindsIndexedPropertiesToTypedArrays() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "nodes[0]", "alpha", "nodes[1]", "beta",
                "ports[0]", "8100", "ports[1]", "8200")));

        assertThat(binder.bind("nodes", String[].class).get()).containsExactly("alpha", "beta");
        assertThat(binder.bind("ports", int[].class).get()).containsExactly(8100, 8200);
    }
}
