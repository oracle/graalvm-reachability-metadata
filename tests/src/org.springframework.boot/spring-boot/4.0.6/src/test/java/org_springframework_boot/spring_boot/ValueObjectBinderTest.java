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
    void bindConstructorUsesEmptyDefaultValueForUnboundArrayParameter() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of("sample.name", "test"));

        ArrayDefaultProperties properties = new Binder(source).bind("sample", ArrayDefaultProperties.class).get();

        assertThat(properties.name()).isEqualTo("test");
        assertThat(properties.tags()).isEmpty();
    }

    public record ArrayDefaultProperties(String name, @DefaultValue String[] tags) {

    }

}
