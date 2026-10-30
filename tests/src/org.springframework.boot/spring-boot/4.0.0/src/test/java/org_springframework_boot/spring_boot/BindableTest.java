/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class BindableTest {
    @Test
    void boxesPrimitiveTargetsAndBindsTheirValues() {
        Bindable<Integer> port = Bindable.of(int.class);
        Bindable<boolean[]> flags = Bindable.of(boolean[].class);
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "port", "8123", "flags[0]", "true", "flags[1]", "false")));

        assertThat(port.getBoxedType().resolve()).isEqualTo(Integer.class);
        assertThat(flags.getBoxedType().resolve()).isEqualTo(Boolean[].class);
        assertThat(binder.bind("port", port).get()).isEqualTo(8123);
        assertThat(binder.bind("flags", flags).get()).containsExactly(true, false);
    }
}
