/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_app_model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.paths.PathTreeWithManifest;

public class PathTreeWithManifestTest {
    @Test
    public void exposesCurrentJavaVersion() {
        final int expectedJavaVersion = Runtime.version().feature();
        final int actualJavaVersion = PathTreeWithManifest.JAVA_VERSION;

        assertEquals(expectedJavaVersion, actualJavaVersion);
        assertTrue(actualJavaVersion > 0);
    }
}
