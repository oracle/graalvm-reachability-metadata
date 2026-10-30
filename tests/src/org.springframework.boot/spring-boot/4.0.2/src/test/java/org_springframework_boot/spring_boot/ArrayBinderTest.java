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
    void bindsIndexedPropertiesToAnArrayOfValueObjects() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "nodes[0].name", "primary", "nodes[0].port", "8080",
                "nodes[1].name", "backup", "nodes[1].port", "9090")));

        Node[] nodes = binder.bind("nodes", Node[].class).get();

        assertThat(nodes).containsExactly(new Node("primary", 8080), new Node("backup", 9090));
    }

    public record Node(String name, int port) {
    }
}
