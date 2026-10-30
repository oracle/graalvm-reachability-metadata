/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.support.SpringFactoriesLoader;

/** Verifies the factory-discovered registrar contributes constructors for configured factories. */
public class SpringFactoriesLoaderRuntimeHintsTest {
    @Test
    void registersFactoriesFromClasspathDescriptors() {
        RuntimeHints hints = new RuntimeHints();
        ClassLoader loader = getClass().getClassLoader();
        RuntimeHintsRegistrar registrar = SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories", loader)
                .load(RuntimeHintsRegistrar.class).stream()
                .filter(candidate -> candidate.getClass().getName().equals(
                        "org.springframework.aot.hint.support.SpringFactoriesLoaderRuntimeHints"))
                .findFirst().orElseThrow();

        registrar.registerHints(hints, loader);

        assertThat(hints.reflection().getTypeHint(SpringFactoriesLoaderTest.FactoryImplementation.class)
                .getMemberCategories()).contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        assertThat(hints.resources().resourcePatternHints()
                .flatMap(patterns -> patterns.getIncludes().stream()).map(pattern -> pattern.getPattern()))
                .contains("META-INF/spring.factories");
    }
}
