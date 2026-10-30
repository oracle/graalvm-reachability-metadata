/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.support.SpringFactoriesLoader;
import tools.jackson.databind.PropertyNamingStrategies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JacksonAutoConfigurationInnerJacksonAutoConfigurationRuntimeHintsTest {
    @Test
    void discoversJacksonHintsAndRegistersPropertyNamingStrategyFields() {
        ClassLoader classLoader = getClass().getClassLoader();
        List<RuntimeHintsRegistrar> registrars = SpringFactoriesLoader
                .forResourceLocation("META-INF/spring/aot.factories", classLoader)
                .load(RuntimeHintsRegistrar.class)
                .stream()
                .filter(registrar -> registrar.getClass().getName().equals(
                        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration$JacksonAutoConfigurationRuntimeHints"))
                .toList();
        assertEquals(1, registrars.size());
        RuntimeHints hints = new RuntimeHints();
        registrars.getFirst().registerHints(hints, classLoader);
        assertTrue(RuntimeHintsPredicates.reflection().onField(PropertyNamingStrategies.class, "SNAKE_CASE").test(hints));
        assertTrue(RuntimeHintsPredicates.reflection().onField(PropertyNamingStrategies.class, "LOWER_CAMEL_CASE").test(hints));
        assertTrue(RuntimeHintsPredicates.reflection().onField(PropertyNamingStrategies.class, "KEBAB_CASE").test(hints));
    }
}
