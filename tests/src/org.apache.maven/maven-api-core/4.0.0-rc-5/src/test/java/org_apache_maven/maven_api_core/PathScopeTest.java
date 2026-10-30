/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_core;

import java.util.Set;

import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.ProjectScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PathScopeTest {
    @Test
    void separatesMainCompileAndRuntimeDependencies() {
        assertEquals(ProjectScope.MAIN, PathScope.MAIN_COMPILE.projectScope());
        assertEquals(Set.of(DependencyScope.COMPILE_ONLY, DependencyScope.COMPILE, DependencyScope.PROVIDED),
                PathScope.MAIN_COMPILE.dependencyScopes());
        assertEquals(ProjectScope.MAIN, PathScope.MAIN_RUNTIME.projectScope());
        assertEquals(Set.of(DependencyScope.COMPILE, DependencyScope.RUNTIME),
                PathScope.MAIN_RUNTIME.dependencyScopes());
    }

    @Test
    void separatesTestCompileAndRuntimeDependencies() {
        assertEquals(ProjectScope.TEST, PathScope.TEST_COMPILE.projectScope());
        assertEquals(Set.of(DependencyScope.COMPILE, DependencyScope.PROVIDED, DependencyScope.TEST_ONLY,
                DependencyScope.TEST), PathScope.TEST_COMPILE.dependencyScopes());
        assertEquals(ProjectScope.TEST, PathScope.TEST_RUNTIME.projectScope());
        assertEquals(Set.of(DependencyScope.COMPILE, DependencyScope.RUNTIME, DependencyScope.PROVIDED,
                DependencyScope.TEST, DependencyScope.TEST_RUNTIME), PathScope.TEST_RUNTIME.dependencyScopes());
        assertThrows(UnsupportedOperationException.class,
                () -> PathScope.TEST_RUNTIME.dependencyScopes().add(DependencyScope.SYSTEM));
    }
}
