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
    void registersBeanConstructorAndAccessorHints() {
        RuntimeHints hints = new RuntimeHints();
        BindableRuntimeHintsRegistrar.forTypes(Preferences.class).registerHints(hints);
        TypeHint typeHint = hints.reflection().getTypeHint(Preferences.class);

        assertThat(typeHint).isNotNull();
        assertThat(typeHint.constructors().anyMatch(hint -> hint.getParameterTypes().isEmpty()
                && hint.getMode() == ExecutableMode.INVOKE)).isTrue();
        assertThat(typeHint.methods().map(hint -> hint.getName())).contains("getTheme", "setTheme");
    }

    public static class Preferences {
        private String theme;

        public String getTheme() {
            return this.theme;
        }

        public void setTheme(String theme) {
            this.theme = theme;
        }
    }
}
