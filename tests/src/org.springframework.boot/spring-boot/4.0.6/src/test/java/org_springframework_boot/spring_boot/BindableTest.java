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
    void boxesPrimitiveBindingTypesAndBindsValues() {
        Bindable<Integer> target = Bindable.of(int.class);
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("workers", "6")));

        assertThat(target.getType().resolve()).isEqualTo(int.class);
        assertThat(target.getBoxedType().resolve()).isEqualTo(Integer.class);
        assertThat(binder.bind("workers", target).get()).isEqualTo(6);
        assertThat(Bindable.of(int[][].class).getBoxedType().resolve()).isEqualTo(Integer[][].class);
    }
}
