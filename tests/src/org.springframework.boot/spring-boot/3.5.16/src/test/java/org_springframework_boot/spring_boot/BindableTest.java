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

public class BindableTest {

    @Test
    void boxesPrimitiveTargetTypes() {
        Bindable<Integer> port = Bindable.of(int.class);
        Bindable<Boolean> enabled = Bindable.of(boolean.class);

        assertThat(port.getType().resolve()).isEqualTo(int.class);
        assertThat(port.getBoxedType().resolve()).isEqualTo(Integer.class);
        assertThat(enabled.getBoxedType().resolve()).isEqualTo(Boolean.class);
    }

    @Test
    void bindsPrimitiveTargetsFromProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("app.port", "8080");
        properties.put("app.enabled", "true");
        Binder binder = new Binder(new MapConfigurationPropertySource(properties));

        assertThat(binder.bind("app.port", Bindable.of(int.class)).get()).isEqualTo(8080);
        assertThat(binder.bind("app.enabled", Bindable.of(boolean.class)).get()).isTrue();
        assertThat(binder.bind("app.missing", Bindable.of(long.class)).orElse(42L)).isEqualTo(42L);
    }
}
