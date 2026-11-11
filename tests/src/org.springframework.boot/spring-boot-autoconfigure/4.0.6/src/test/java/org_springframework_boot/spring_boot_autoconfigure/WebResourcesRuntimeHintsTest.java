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
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.boot.autoconfigure.web.WebResourcesRuntimeHints;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebResourcesRuntimeHintsTest {
    @Test
    void registersExistingWebResourceLocationsWithTheApplicationClassLoader() {
        RuntimeHints hints = new RuntimeHints();
        new WebResourcesRuntimeHints().registerHints(hints, getClass().getClassLoader());
        assertWebResourcesIncluded(hints);
    }

    @Test
    void registersExistingWebResourceLocationsWithTheDefaultClassLoader() {
        RuntimeHints hints = new RuntimeHints();
        new WebResourcesRuntimeHints().registerHints(hints, null);
        assertWebResourcesIncluded(hints);
    }

    private void assertWebResourcesIncluded(RuntimeHints hints) {
        for (String resource : List.of("META-INF/resources/assets/catalog.txt", "resources/assets/catalog.txt",
                "static/assets/catalog.txt", "public/assets/catalog.txt")) {
            assertTrue(RuntimeHintsPredicates.resource().forResource(resource).test(hints), resource);
        }
        assertFalse(RuntimeHintsPredicates.resource().forResource("features/orders.txt").test(hints));
    }
}
