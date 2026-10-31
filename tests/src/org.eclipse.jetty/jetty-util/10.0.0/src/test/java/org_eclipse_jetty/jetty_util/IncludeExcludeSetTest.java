/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.IncludeExcludeSet;
import org.eclipse.jetty.util.RegexSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IncludeExcludeSetTest {
    @Test
    void appliesExactIncludesAndGivesExcludesPrecedence() {
        IncludeExcludeSet<String, String> rules = new IncludeExcludeSet<>();
        assertTrue(rules.test("public"));
        rules.include("public", "private");
        rules.exclude("private");
        assertTrue(rules.test("public"));
        assertFalse(rules.test("private"));
        assertFalse(rules.test("other"));
        assertEquals(Boolean.TRUE, rules.isIncludedAndNotExcluded("public"));
        assertEquals(Boolean.FALSE, rules.isIncludedAndNotExcluded("private"));
    }

    @Test
    void instantiatesPredicateBackedSetsForPatternRules() {
        IncludeExcludeSet<String, String> rules = new IncludeExcludeSet<>(RegexSet.class);
        rules.include("/api/.*");
        rules.exclude("/api/internal/.*");
        assertTrue(rules.test("/api/catalog"));
        assertFalse(rules.test("/api/internal/secrets"));
        assertFalse(rules.test("/static/logo.png"));
        assertEquals(2, rules.size());
        rules.clear();
        assertTrue(rules.isEmpty());
        assertTrue(rules.test("/static/logo.png"));
    }
}
