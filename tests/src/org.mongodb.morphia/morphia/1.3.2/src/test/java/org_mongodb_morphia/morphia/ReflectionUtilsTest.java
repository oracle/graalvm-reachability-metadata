/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class ReflectionUtilsTest {
    private static final String TEST_PACKAGE = ReflectionUtilsTest.class.getPackage().getName();
    private static final String TEST_PACKAGE_PATH = TEST_PACKAGE.replace('.', '/');
    private static final String TEST_CLASS_FILE = ReflectionUtilsTest.class.getSimpleName() + ".class";

    @Test
    void declaredAndInheritedFieldsIncludesChildAndParentFields() {
        Field[] fields = ReflectionUtils.getDeclaredAndInheritedFields(ReflectionUtilsChildPojo.class, true);

        assertThat(fields)
                .extracting(Field::getName)
                .contains("parentField", "parentFinalField", "childField", "childFinalField")
                .doesNotContain("parentStaticField", "childStaticField");
    }

    @Test
    void declaredAndInheritedMethodsIncludesChildAndParentMethods() {
        List<Method> methods = ReflectionUtils.getDeclaredAndInheritedMethods(ReflectionUtilsChildPojo.class);

        assertThat(methods)
                .extracting(Method::getName)
                .contains("parentMethod", "childMethod")
                .doesNotContain("parentStaticMethod", "childStaticMethod");
    }

    @Test
    void convertsListsToReferenceAndPrimitiveArrays() {
        Object stringArray = ReflectionUtils.convertToArray(String.class, Arrays.asList("one", "two"));
        Object intArray = ReflectionUtils.convertToArray(int.class, Arrays.asList(1, 2, 3));

        assertThat((String[]) stringArray).containsExactly("one", "two");
        assertThat((int[]) intArray).containsExactly(1, 2, 3);
    }

    @Test
    void resolvesGenericArrayTypeToArrayClass() throws Exception {
        Field field = requiredField(ReflectionUtilsGenericTypePojo.class, "parameterizedArray");
        Type genericArrayType = field.getGenericType();

        Class<?> arrayClass = ReflectionUtils.getClass(genericArrayType);

        assertThat(arrayClass).isEqualTo(List[].class);
    }

    @Test
    void reportsUnsupportedParameterizedGenericArrays() throws Exception {
        Field field = requiredField(ReflectionUtilsGenericTypePojo.class, "typeVariableArrays");

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> ReflectionUtils.getParameterizedClass(field));
    }

    @Test
    void scansKnownClassesFromDirectory(@TempDir Path packageDirectory) throws Exception {
        Files.createFile(packageDirectory.resolve(TEST_CLASS_FILE));

        Set<Class<?>> classes = ReflectionUtils.getFromDirectory(currentClassLoader(), packageDirectory.toFile(),
                TEST_PACKAGE);

        assertThat(classes).contains(ReflectionUtilsTest.class);
    }

    @Test
    void scansKnownClassesFromJar(@TempDir Path tempDirectory) throws Exception {
        Path jarFile = tempDirectory.resolve("reflection-utils-scan.jar");
        createJarWithTestClassEntry(jarFile);

        Set<Class<?>> classes = ReflectionUtils.getFromJARFile(currentClassLoader(), jarFile.toString(),
                TEST_PACKAGE_PATH);

        assertThat(classes).contains(ReflectionUtilsTest.class);
    }

    @Test
    void scansKnownClassesFromClassLoaderResources(@TempDir Path tempDirectory) throws Exception {
        Path packageDirectory = Files.createDirectories(tempDirectory.resolve(TEST_PACKAGE_PATH));
        Files.createFile(packageDirectory.resolve(TEST_CLASS_FILE));
        ClassLoader loader = new FixedResourceClassLoader(currentClassLoader(), TEST_PACKAGE_PATH,
                packageDirectory.toUri().toURL());

        Set<Class<?>> classes = ReflectionUtils.getClasses(loader, TEST_PACKAGE);

        assertThat(classes).contains(ReflectionUtilsTest.class);
    }

    private static ClassLoader currentClassLoader() {
        return ReflectionUtilsTest.class.getClassLoader();
    }

    private static Field requiredField(final Class<?> type, final String fieldName) throws NoSuchFieldException {
        return type.getDeclaredField(fieldName);
    }

    private static void createJarWithTestClassEntry(final Path jarFile) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarFile))) {
            output.putNextEntry(new JarEntry(TEST_PACKAGE_PATH + '/' + TEST_CLASS_FILE));
            output.closeEntry();
        }
    }
}

final class FixedResourceClassLoader extends ClassLoader {
    private final String resourceName;
    private final URL resource;

    FixedResourceClassLoader(final ClassLoader parent, final String resourceName, final URL resource) {
        super(parent);
        this.resourceName = resourceName;
        this.resource = resource;
    }

    @Override
    public Enumeration<URL> getResources(final String name) throws IOException {
        if (resourceName.equals(name)) {
            return Collections.enumeration(Arrays.asList(resource));
        }
        return super.getResources(name);
    }
}

class ReflectionUtilsParentPojo {
    static String parentStaticField;
    String parentField;
    final String parentFinalField = "parent";

    void parentMethod() {
    }

    static void parentStaticMethod() {
    }
}

class ReflectionUtilsChildPojo extends ReflectionUtilsParentPojo {
    static String childStaticField;
    int childField;
    final int childFinalField = 1;

    void childMethod() {
    }

    static void childStaticMethod() {
    }
}

class ReflectionUtilsGenericTypePojo<T> {
    List<String>[] parameterizedArray;
    List<T[]> typeVariableArrays;
}
