/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;

public class ClassGraphClassLoaderTest {
    @Test
    void loadsScannedClassThroughEnvironmentClassLoader() {
        try (ScanResult scanResult = scanTestPackage()) {
            assertThat(scanResult.getAllClasses()).isNotEmpty();
            assertThat(scanResult.loadClass(ScanTarget.class.getName(), false)).isEqualTo(ScanTarget.class);
        }
    }

    @Test
    void loadsScannedClassThroughOverrideClassLoader() {
        try (ScanResult scanResult = new ClassGraph().disableModuleScanning()
                .overrideClassLoaders(ClassGraphClassLoaderTest.class.getClassLoader())
                .acceptPackages(ClassGraphClassLoaderTest.class.getPackageName()).scan()) {
            assertThat(scanResult.getAllClasses()).isNotEmpty();
            assertThat(scanResult.loadClass(ScanTarget.class.getName(), false)).isEqualTo(ScanTarget.class);
        }
    }

    @Test
    void returnsNullForMissingClassAfterCheckingAddedClassLoader() {
        try (ScanResult scanResult = new ClassGraph().disableModuleScanning()
                .addClassLoader(new BootstrapOnlyClassLoader())
                .acceptPackages(ClassGraphClassLoaderTest.class.getPackageName()).scan()) {
            assertThat(scanResult.getAllClasses()).isNotEmpty();
            assertThat(scanResult.loadClass("io_github_classgraph.classgraph.MissingScanTarget", true)).isNull();
        }
    }

    private ScanResult scanTestPackage() {
        return new ClassGraph().disableModuleScanning()
                .acceptPackages(ClassGraphClassLoaderTest.class.getPackageName()).scan();
    }

    private static final class BootstrapOnlyClassLoader extends ClassLoader {
        private BootstrapOnlyClassLoader() {
            super(null);
        }
    }

    public static final class ScanTarget {
    }
}
