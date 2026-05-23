/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mongodb.morphia.utils.ReflectionUtils;

public class ReflectionUtilsTest {
    private static final String TEST_PACKAGE = ReflectionUtilsTest.class.getPackage().getName();
    private static final String TEST_PACKAGE_PATH = TEST_PACKAGE.replace('.', '/');

    @Test
    void declaredFieldScannerReturnsInstanceFieldsFromClassHierarchy() {
        Field[] fields = ReflectionUtils.getDeclaredAndInheritedFields(FieldChild.class, false);

        assertThat(Arrays.stream(fields).map(Field::getName))
                .contains("childField", "parentField")
                .doesNotContain("childFinalField", "parentFinalField", "staticField");
    }

    @Test
    void declaredMethodScannerReturnsInstanceMethodsFromClassHierarchy() {
        List<Method> methods = ReflectionUtils.getDeclaredAndInheritedMethods(MethodChild.class);

        assertThat(methods.stream().map(Method::getName))
                .contains("childMethod", "parentMethod")
                .doesNotContain("staticMethod");
    }

    @Test
    void arrayUtilitiesResolveConcreteAndGenericArrayTypes() throws Exception {
        Object array = ReflectionUtils.convertToArray(String.class, Arrays.asList("one", "two"));
        Field parameterizedArray = GenericArrayFixture.class.getDeclaredField("parameterizedArray");

        assertThat(array).isInstanceOf(String[].class);
        assertThat((String[]) array).containsExactly("one", "two");
        assertThat(ReflectionUtils.getClass(parameterizedArray.getGenericType()))
                .isEqualTo(List[].class);
    }

    @Test
    void packageScannerFindsClassesFromDirectoryResources(@TempDir Path classPathRoot) throws Exception {
        Path classPackageDirectory = writeClassMarker(classPathRoot, DirectoryScannedFixture.class);
        ClassLoader loader = new DirectoryResourceClassLoader(classPackageDirectory.toUri().toURL());

        Set<Class<?>> classes = ReflectionUtils.getClasses(loader, TEST_PACKAGE, false);

        assertThat(classes).contains(DirectoryScannedFixture.class);
    }

    @Test
    void jarScannerFindsClassesFromJarEntries(@TempDir Path tempDir) throws Exception {
        Path jar = tempDir.resolve("reflection-utils-fixtures.jar");
        writeJarMarker(jar, JarScannedFixture.class);

        Set<Class<?>> classes = ReflectionUtils.getFromJARFile(
                ReflectionUtilsTest.class.getClassLoader(),
                jar.toString(),
                TEST_PACKAGE_PATH,
                false);

        assertThat(classes).contains(JarScannedFixture.class);
    }

    @Test
    void directoryScannerFindsClassesFromClassFileNames(@TempDir Path packageDirectory) throws Exception {
        Path classPackageDirectory = writeClassMarker(packageDirectory, DirectoryScannedFixture.class);

        Set<Class<?>> classes = ReflectionUtils.getFromDirectory(
                ReflectionUtilsTest.class.getClassLoader(),
                classPackageDirectory.toFile(),
                TEST_PACKAGE,
                false);

        assertThat(classes).contains(DirectoryScannedFixture.class);
    }

    private static Path writeClassMarker(Path rootOrPackageDirectory, Class<?> type) throws Exception {
        Path packageDirectory = packageDirectory(rootOrPackageDirectory);
        Files.createDirectories(packageDirectory);
        Files.write(packageDirectory.resolve(classFileName(type)), new byte[0]);
        return packageDirectory;
    }

    private static Path packageDirectory(Path rootOrPackageDirectory) {
        Path expectedPackageSuffix = Path.of(TEST_PACKAGE_PATH);
        if (rootOrPackageDirectory.endsWith(expectedPackageSuffix)) {
            return rootOrPackageDirectory;
        }
        return rootOrPackageDirectory.resolve(TEST_PACKAGE_PATH);
    }

    private static void writeJarMarker(Path jar, Class<?> type) throws Exception {
        try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jar))) {
            outputStream.putNextEntry(new JarEntry(TEST_PACKAGE_PATH + "/" + classFileName(type)));
            outputStream.closeEntry();
        }
    }

    private static String classFileName(Class<?> type) {
        return type.getName().substring(TEST_PACKAGE.length() + 1) + ".class";
    }

    private static class DirectoryResourceClassLoader extends ClassLoader {
        private final URL packageDirectory;

        DirectoryResourceClassLoader(URL packageDirectory) {
            super(ReflectionUtilsTest.class.getClassLoader());
            this.packageDirectory = packageDirectory;
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            if (TEST_PACKAGE_PATH.equals(name)) {
                return Collections.enumeration(List.of(packageDirectory));
            }
            try {
                return super.getResources(name);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static class FieldParent {
        private static String staticField;
        private final String parentFinalField = "parent";
        private String parentField;
    }

    private static class FieldChild extends FieldParent {
        private static String staticField;
        private final String childFinalField = "child";
        private String childField;
    }

    private static class MethodParent {
        void parentMethod() {
        }
    }

    private static class MethodChild extends MethodParent {
        void childMethod() {
        }

        static void staticMethod() {
        }
    }

    private static class GenericArrayFixture {
        private List<String>[] parameterizedArray;
    }

    private static class DirectoryScannedFixture {
    }

    private static class JarScannedFixture {
    }
}
