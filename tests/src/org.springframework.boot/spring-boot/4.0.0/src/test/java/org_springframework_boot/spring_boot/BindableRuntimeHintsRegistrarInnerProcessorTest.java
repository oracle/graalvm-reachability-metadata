/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.boot.context.properties.bind.BindableRuntimeHintsRegistrar;

import static org.assertj.core.api.Assertions.assertThat;

public class BindableRuntimeHintsRegistrarInnerProcessorTest {
    @Test
    void discoversDefaultConstructorAndPropertyHints() {
        RuntimeHints hints = new RuntimeHints();

        BindableRuntimeHintsRegistrar.forTypes(Options.class).registerHints(hints);

        TypeHint type = hints.reflection().getTypeHint(Options.class);
        assertThat(type).isNotNull();
        assertThat(type.constructors()).anySatisfy(constructor -> {
            assertThat(constructor.getParameterTypes()).isEmpty();
            assertThat(constructor.getMode()).isEqualTo(ExecutableMode.INVOKE);
        });
        assertThat(type.methods().map(method -> method.getName())).contains("getCount", "setCount");
    }

    public static class Options {
        private int count;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
