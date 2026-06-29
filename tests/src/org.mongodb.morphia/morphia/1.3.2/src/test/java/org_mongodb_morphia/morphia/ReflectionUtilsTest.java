/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mongodb.morphia.logging.jdk.FasterJDKLogger;
import org.mongodb.morphia.utils.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    private static final String MORPHIA_UTILS_PACKAGE = "org.mongodb.morphia.utils";
    private static final String MORPHIA_UTILS_PATH = "org/mongodb/morphia/utils";

    @Test
    public void getsDeclaredAndInheritedFieldsFromLibraryTypes() {
        final Field[] fields = ReflectionUtils.getDeclaredAndInheritedFields(FasterJDKLogger.class, true);
        final List<String> fieldNames = Arrays.stream(fields)
                .map(Field::getName)
                .collect(Collectors.toList());

        assertThat(fieldNames).contains("logger", "className");
    }

    @Test
    public void getsDeclaredAndInheritedMethodsFromLibraryTypes() {
        final List<Method> methods = ReflectionUtils.getDeclaredAndInheritedMethods(FasterJDKLogger.class);
        final List<String> methodNames = methods.stream()
                .map(Method::getName)
                .collect(Collectors.toList());

        assertThat(methodNames).contains("debug", "getLogger", "getCallingMethod");
    }

    @Test
    public void getsClassesFromSuppliedLoaderResources(@TempDir final Path temporaryDirectory) throws Exception {
        Files.createFile(temporaryDirectory.resolve("ReflectionUtils.class"));
        final ClassLoader loader = new ResourceOnlyClassLoader(
                currentClassLoader(), MORPHIA_UTILS_PATH, temporaryDirectory.toUri().toURL());

        final Set<Class<?>> classes = ReflectionUtils.getClasses(loader, MORPHIA_UTILS_PACKAGE);

        assertThat(classes).contains(ReflectionUtils.class);
    }

    @Test
    public void getsClassesFromJarFileEntries(@TempDir final Path temporaryDirectory) throws Exception {
        final Path jar = temporaryDirectory.resolve("morphia-utils.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(MORPHIA_UTILS_PATH + "/ReflectionUtils.class"));
            output.closeEntry();
        }

        final Set<Class<?>> classes = ReflectionUtils.getFromJARFile(
                currentClassLoader(), jar.toString(), MORPHIA_UTILS_PATH);

        assertThat(classes).containsExactly(ReflectionUtils.class);
    }

    @Test
    public void convertsListValuesToTypedArray() {
        final Object array = ReflectionUtils.convertToArray(String.class, Arrays.asList("left", "right"));

        assertThat(array).isInstanceOf(String[].class);
        assertThat((String[]) array).containsExactly("left", "right");
    }

    @Test
    public void resolvesGenericArrayTypesToArrayClasses() {
        final Class<?> arrayClass = ReflectionUtils.getClass(new GenericListArrayType());

        assertThat(arrayClass).isEqualTo(List[].class);
    }

    private static ClassLoader currentClassLoader() {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            return contextClassLoader;
        }
        final ClassLoader morphiaClassLoader = ReflectionUtils.class.getClassLoader();
        if (morphiaClassLoader != null) {
            return morphiaClassLoader;
        }
        return ClassLoader.getSystemClassLoader();
    }

    private static final class ResourceOnlyClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;

        private ResourceOnlyClassLoader(final ClassLoader parent, final String resourceName, final URL resourceUrl) {
            super(parent);
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
        }

        @Override
        public Enumeration<URL> getResources(final String name) throws IOException {
            if (resourceName.equals(name)) {
                return Collections.enumeration(Collections.singleton(resourceUrl));
            }
            return super.getResources(name);
        }
    }

    private static final class GenericListArrayType implements GenericArrayType {
        @Override
        public Type getGenericComponentType() {
            return new ListOfStringType();
        }
    }

    private static final class ListOfStringType implements ParameterizedType {
        @Override
        public Type[] getActualTypeArguments() {
            return new Type[] {String.class};
        }

        @Override
        public Type getRawType() {
            return List.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }
}
