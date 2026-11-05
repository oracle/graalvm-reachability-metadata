/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassLoaderHandlerRegistryInnerClassLoaderHandlerRegistryEntryTest {
    @TempDir
    Path directory;

    @Test
    void resolvesClasspathUsingEnvironmentClassLoaderHandlers() throws Exception {
        try (ScanResult scan = ScanSupport.discover(ScanSupport.archive(directory))) {
            assertTrue(scan.getClassesImplementing(Runnable.class.getName()).getNames()
                    .contains(ScanFixtures.Subject.class.getName()));
            assertTrue(scan.getClassesImplementing(Runnable.class.getName()).getNames()
                    .contains(ScanFixtures.Child.class.getName()));
        }
    }
}
