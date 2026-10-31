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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClassGraphClassLoaderTest {
    @TempDir
    Path directory;

    @Test
    void delegatesToExistingApplicationAndBootstrapClasses() throws Exception {
        try (ScanResult scan = ScanSupport.discover(ScanSupport.archive(directory))) {
            assertSame(String.class, scan.loadClass(String.class.getName(), false));
            assertSame(ScanFixtures.Child.class, scan.loadClass(ScanFixtures.Child.class.getName(), false));
        }
    }

    @Test
    void fallsBackToAddedApplicationLoader() throws Exception {
        try (ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory))
                .overrideClassLoaders(ClassLoader.getPlatformClassLoader())
                .addClassLoader(ScanFixtures.class.getClassLoader()).scan(2)) {
            assertSame(ScanFixtures.Subject.class,
                    scan.loadClass(ScanFixtures.Subject.class.getName(), Runnable.class, false));
            assertSame(ScanFixtures.Child.class,
                    scan.loadClass(ScanFixtures.Child.class.getName(), Runnable.class, false));
        }
    }

    @Test
    void rejectsUnknownClassAfterSearchingClasspath() throws Exception {
        try (ScanResult scan = ScanSupport.discover(ScanSupport.archive(directory))) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> scan.loadClass(ScanSupport.PACKAGE + ".AbsentCatalogEntry", false));
            assertSame(ClassNotFoundException.class, exception.getCause().getClass());
        }
    }
}
