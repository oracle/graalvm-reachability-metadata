/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

final class TestClassImporter {
    private static final Path TEST_CLASSES = Path.of("build", "classes", "java", "test");

    private TestClassImporter() { }

    static JavaClass importClass(Class<?> type) {
        return importClasses(type).get(type);
    }

    static JavaClasses importClasses(Class<?>... types) {
        ArchConfiguration configuration = ArchConfiguration.get();
        boolean resolveDependencies = configuration.resolveMissingDependenciesFromClassPath();
        try {
            configuration.setResolveMissingDependenciesFromClassPath(false);
            return importClassesWithConfiguredResolver(types);
        } finally {
            configuration.setResolveMissingDependenciesFromClassPath(resolveDependencies);
        }
    }

    static JavaClass importClassWithConfiguredResolver(Class<?> type) {
        return importClassesWithConfiguredResolver(type).get(type);
    }

    private static JavaClasses importClassesWithConfiguredResolver(Class<?>... types) {
        List<Path> classFiles = Arrays.stream(types)
                .map(type -> TEST_CLASSES.resolve(type.getName().replace('.', '/') + ".class"))
                .toList();
        return new ClassFileImporter().importPaths(classFiles);
    }
}
