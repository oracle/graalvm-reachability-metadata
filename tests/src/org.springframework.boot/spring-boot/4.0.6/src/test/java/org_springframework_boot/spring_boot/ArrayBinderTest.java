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
    void bindIndexedPropertiesToArray() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(
                Map.of("sample.values[0]", "alpha", "sample.values[1]", "bravo"));

        String[] values = new Binder(source).bind("sample.values", String[].class).get();

        assertThat(values).containsExactly("alpha", "bravo");
    }

}
