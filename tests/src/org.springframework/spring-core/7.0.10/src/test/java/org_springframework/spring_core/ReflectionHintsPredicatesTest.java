/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

/** Verifies string-based member predicates against populated and empty hints. */
public class ReflectionHintsPredicatesTest {
    @Test
    @SuppressWarnings("removal")
    void resolvesNamedMethodsAndFields() throws Exception {
        RuntimeHints hints = new RuntimeHints();
        hints.reflection().registerType(Settings.class, builder -> builder
                .withField("label")
                .withMethod("describe", List.of(), ExecutableMode.INVOKE));
        String typeName = Settings.class.getName();

        assertThat(RuntimeHintsPredicates.reflection().onMethod(typeName, "describe").test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(typeName, "describe").test(hints))
                .isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onFieldAccess(typeName, "label").test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(typeName, "describe")
                .test(new RuntimeHints())).isFalse();
        assertThat(RuntimeHintsPredicates.reflection().onFieldAccess(typeName, "label")
                .test(new RuntimeHints())).isFalse();
    }

    public static class Settings {
        public String label = "configured";

        public String describe() {
            return label;
        }
    }
}
