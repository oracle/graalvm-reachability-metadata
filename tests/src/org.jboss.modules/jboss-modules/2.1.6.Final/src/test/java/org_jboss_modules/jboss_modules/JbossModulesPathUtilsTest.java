/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_modules.jboss_modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.jboss.modules.PathUtils;
import org.jboss.modules.filter.PathFilters;
import org.junit.jupiter.api.Test;

public class JbossModulesPathUtilsTest {
    @Test
    void canonicalPathOperationsNormalizeRelativeSegmentsAndClassifyChildren() {
        assertEquals("alpha/gamma/delta", PathUtils.canonicalize("alpha/beta/../gamma/./delta"));
        assertEquals("var/log", PathUtils.relativize("///var/log"));
        assertEquals("archive.jar", PathUtils.fileNameOfPath("/tmp/archive.jar"));
        assertEquals("alpha/beta", PathUtils.toGenericSeparators("alpha/beta"));

        assertTrue(PathUtils.isRelative("alpha/beta"));
        assertFalse(PathUtils.isRelative("/alpha/beta"));
        assertTrue(PathUtils.isSeparator('/'));

        assertTrue(PathUtils.isChild("alpha", "alpha/beta/gamma"));
        assertTrue(PathUtils.isDirectChild("alpha", "alpha/beta"));
        assertFalse(PathUtils.isDirectChild("alpha", "alpha/beta/gamma"));
        assertThrows(IllegalArgumentException.class, () -> PathUtils.isChild("alpha", "/alpha/beta"));
    }

    @Test
    void filterPathsAddsAcceptedPathsToTheSuppliedCollection() {
        List<String> paths = List.of("api/Foo.class", "api/internal/Secret.class", "META-INF/services/app.Plugin");
        LinkedHashSet<String> acceptedPaths = new LinkedHashSet<>();

        LinkedHashSet<String> returnedPaths = PathUtils.filterPaths(
                paths,
                PathFilters.all(
                        PathFilters.isChildOf("api"),
                        PathFilters.not(PathFilters.isChildOf("api/internal"))),
                acceptedPaths);

        assertSame(acceptedPaths, returnedPaths);
        assertIterableEquals(List.of("api/Foo.class"), new ArrayList<>(acceptedPaths));
    }

    @Test
    void basicModuleNamesAreConvertedToModulePathSegments() {
        assertEquals("org/jboss/modules/main", PathUtils.basicModuleNameToPath("org.jboss.modules"));
        assertEquals("org/jboss/modules/test-slot", PathUtils.basicModuleNameToPath("org.jboss.modules:test-slot"));
        assertEquals("org/jboss/modules/test.slot", PathUtils.basicModuleNameToPath("org.jboss.modules:test/slot"));

        assertNull(PathUtils.basicModuleNameToPath(".leading"));
        assertNull(PathUtils.basicModuleNameToPath("org..example"));
        assertNull(PathUtils.basicModuleNameToPath("org.example:"));
        assertNull(PathUtils.basicModuleNameToPath("org.example\\"));
    }
}
