/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteArrayClassLoaderInnerChildFirstTest {
    private static final String GENERATED_PACKAGE = "net_bytebuddy.byte_buddy.generated.childfirst";
    private static final String LATENT_TYPE_NAME = GENERATED_PACKAGE + ".LatentChildFirstType";
    private static final String MANIFEST_TYPE_NAME = GENERATED_PACKAGE + ".ManifestChildFirstType";

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsTypesAndResolvesResourcesWithChildFirstSemantics() throws Exception {
        try {
            prepareParentResource("parent-resource.txt", "parent resource".getBytes(StandardCharsets.UTF_8));

            try (URLClassLoader parentClassLoader = parentClassLoader()) {
                Class<?> latentType = loadLatentChildFirstType(parentClassLoader);
                ClassLoader latentClassLoader = latentType.getClassLoader();

                assertThat(latentClassLoader).isInstanceOf(ByteArrayClassLoader.ChildFirst.class);
                assertThat(latentClassLoader.getResource("parent-resource.txt")).isNotNull();

                Enumeration<URL> parentResources = latentClassLoader.getResources("parent-resource.txt");
                assertThat(parentResources.hasMoreElements()).isTrue();
                assertThat(read(parentResources.nextElement())).containsExactly(
                        "parent resource".getBytes(StandardCharsets.UTF_8));

                String latentResourceName = latentType.getName().replace('.', '/') + ".class";
                assertThat(latentClassLoader.getResource(latentResourceName)).isNull();

                loadManifestChildFirstTypeAndReadClassResource(parentClassLoader);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private Class<?> loadLatentChildFirstType(ClassLoader parentClassLoader) {
        DynamicType.Unloaded<?> unloaded = makeType(LATENT_TYPE_NAME);
        Map<String, byte[]> typeDefinitions = new LinkedHashMap<String, byte[]>();
        String typeName = unloaded.getTypeDescription().getName();
        typeDefinitions.put(typeName, unloaded.getBytes());
        ExposedChildFirstClassLoader classLoader = new ExposedChildFirstClassLoader(
                parentClassLoader,
                typeDefinitions,
                ByteArrayClassLoader.PersistenceHandler.LATENT);
        try {
            return classLoader.define(typeName);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot load class " + typeName, exception);
        }
    }

    private void loadManifestChildFirstTypeAndReadClassResource(ClassLoader parentClassLoader)
            throws Exception {
        DynamicType.Unloaded<?> unloaded = makeType(MANIFEST_TYPE_NAME);
        String typeName = unloaded.getTypeDescription().getName();
        String resourceName = typeName.replace('.', '/') + ".class";
        prepareParentResource(resourceName, "parent class resource".getBytes(StandardCharsets.UTF_8));

        Map<String, byte[]> typeDefinitions = new LinkedHashMap<String, byte[]>();
        typeDefinitions.put(typeName, unloaded.getBytes());
        ByteArrayClassLoader.ChildFirst classLoader = new ByteArrayClassLoader.ChildFirst(
                parentClassLoader,
                typeDefinitions,
                ByteArrayClassLoader.PersistenceHandler.MANIFEST);

        Class<?> loadedType = classLoader.loadClass(typeName);
        assertThat(loadedType.getClassLoader()).isSameAs(classLoader);

        Enumeration<URL> resources = classLoader.getResources(resourceName);
        assertThat(resources.hasMoreElements()).isTrue();
        assertThat(read(resources.nextElement())).containsExactly(unloaded.getBytes());
    }

    private DynamicType.Unloaded<?> makeType(String typeName) {
        return new ByteBuddy(ClassFileVersion.JAVA_V8)
                .subclass(Object.class)
                .name(typeName)
                .make();
    }

    private URLClassLoader parentClassLoader() throws IOException {
        return new URLClassLoader(
                new URL[] {temporaryDirectory.toUri().toURL()},
                Thread.currentThread().getContextClassLoader());
    }

    private void prepareParentResource(String resourceName, byte[] content) throws IOException {
        Path resource = temporaryDirectory.resolve(resourceName);
        if (resource.getParent() != null) {
            Files.createDirectories(resource.getParent());
        }
        Files.write(resource, content);
    }

    private static byte[] read(URL url) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (InputStream inputStream = url.openStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
        }
        return outputStream.toByteArray();
    }

    private static final class ExposedChildFirstClassLoader extends ByteArrayClassLoader.ChildFirst {
        private ExposedChildFirstClassLoader(
                ClassLoader parentClassLoader,
                Map<String, byte[]> typeDefinitions,
                ByteArrayClassLoader.PersistenceHandler persistenceHandler) {
            super(parentClassLoader, typeDefinitions, persistenceHandler);
        }

        private Class<?> define(String typeName) throws ClassNotFoundException {
            return findClass(typeName);
        }
    }
}
