/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_core;

import org.apache.maven.api.DependencyScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DependencyScopeTest {
    @Test
    void looksUpModelScopeIdentifiers() {
        assertEquals(DependencyScope.COMPILE, DependencyScope.forId("compile"));
        assertEquals(DependencyScope.COMPILE_ONLY, DependencyScope.forId("compile-only"));
        assertEquals(DependencyScope.TEST_RUNTIME, DependencyScope.forId("test-runtime"));
        assertEquals(DependencyScope.UNDEFINED, DependencyScope.forId(""));
        assertTrue(DependencyScope.PROVIDED.is("provided"));
        assertFalse(DependencyScope.PROVIDED.is("compile"));
        assertNull(DependencyScope.forId("COMPILE"));
        assertNull(DependencyScope.forId("unknown-scope"));
    }

    @Test
    void propagatesOnlyCompileAndRuntimeDependenciesTransitively() {
        assertTrue(DependencyScope.COMPILE.isTransitive());
        assertTrue(DependencyScope.RUNTIME.isTransitive());
        assertFalse(DependencyScope.PROVIDED.isTransitive());
        assertFalse(DependencyScope.TEST.isTransitive());
        assertFalse(DependencyScope.COMPILE_ONLY.isTransitive());
        assertFalse(DependencyScope.SYSTEM.isTransitive());
    }
}
