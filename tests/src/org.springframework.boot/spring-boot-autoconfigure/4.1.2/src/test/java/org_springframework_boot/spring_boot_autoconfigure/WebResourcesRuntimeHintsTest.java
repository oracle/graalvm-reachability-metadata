/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.boot.autoconfigure.web.WebResourcesRuntimeHints;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebResourcesRuntimeHintsTest {
    @Test
    void registersExistingStaticResourceLocations() {
        assertStaticResourceHints(getClass().getClassLoader());
    }

    @Test
    void registersStaticResourcesWithTheDefaultClassLoader() {
        assertStaticResourceHints(null);
    }

    private void assertStaticResourceHints(ClassLoader classLoader) {
        RuntimeHints hints = new RuntimeHints();
        new WebResourcesRuntimeHints().registerHints(hints, classLoader);
        assertTrue(RuntimeHintsPredicates.resource().forResource("static/forge-status.txt").test(hints));
        assertTrue(RuntimeHintsPredicates.resource().forResource("public/forge-welcome.txt").test(hints));
        assertFalse(RuntimeHintsPredicates.resource().forResource("private/credentials.txt").test(hints));
    }
}
