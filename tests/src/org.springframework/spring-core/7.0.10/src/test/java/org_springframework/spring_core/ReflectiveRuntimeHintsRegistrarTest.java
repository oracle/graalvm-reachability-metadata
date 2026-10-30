/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.aot.hint.annotation.ReflectiveRuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

/** Verifies discovery of reflective constructors and instantiation of their processors. */
public class ReflectiveRuntimeHintsRegistrarTest {
    @Test
    void registersAnnotatedConstructorAndMethod() {
        RuntimeHints hints = new RuntimeHints();
        ReflectiveRuntimeHintsRegistrar registrar = new ReflectiveRuntimeHintsRegistrar();

        assertThat(registrar.isCandidate(Command.class)).isTrue();
        registrar.registerRuntimeHints(hints, Command.class);

        assertThat(hints.reflection().getTypeHint(Command.class).constructors())
                .anySatisfy(constructor -> {
                    assertThat(constructor.getParameterTypes()).isEmpty();
                    assertThat(constructor.getMode()).isEqualTo(ExecutableMode.INVOKE);
                });
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(Command.class, "execute").test(hints))
                .isTrue();
    }

    public static class Command {
        @Reflective
        public Command() { }

        @Reflective
        public String execute() {
            return "executed";
        }
    }
}
