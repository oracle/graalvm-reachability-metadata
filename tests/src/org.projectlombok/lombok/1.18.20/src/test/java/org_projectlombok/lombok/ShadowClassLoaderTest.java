/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ShadowClassLoaderTest {
    private static final String SHADOW_CLASS_LOADER_NAME = "lombok.launch.ShadowClassLoader";
    private static final String SCL_SUFFIX = "lomboktest";
    private static final byte[] WRONG_NAME_CLASS_BYTES = Base64.getMimeDecoder().decode("""
            yv66vgAAADQADQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+
            AQADKClWBwAIAQAOZXhhbXBsZS9BY3R1YWwBAARDb2RlAQAPTGluZU51bWJlclRhYmxl
            AQAKU291cmNlRmlsZQEAC0FjdHVhbC5qYXZhACEABwACAAAAAAABAAEABQAGAAEACQAA
            AB0AAQABAAAABSq3AAGxAAAAAQAKAAAABgABAAAAAQABAAsAAAACAAw=
            """);

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesResourcesFromParentAndShadowLocations() throws Exception {
        Path selfBase = Files.createDirectory(temporaryDirectory.resolve("self-base"));
        Path parentBase = Files.createDirectory(temporaryDirectory.resolve("parent-base"));
        write(parentBase.resolve("example/AltOnly.SCL." + SCL_SUFFIX), "shadow-resource");
        write(parentBase.resolve("example/plain.txt"), "plain-resource");
        write(parentBase.resolve("META-INF/ShadowClassLoader"), SCL_SUFFIX + "\n");

        try (URLClassLoader parentLoader = urlClassLoader(parentBase)) {
            ClassLoader shadowLoader = newShadowClassLoader(parentLoader, selfBase);

            URL altResource = shadowLoader.getResource("example/AltOnly.class");
            URL plainResource = shadowLoader.getResource("example/plain.txt");
            Enumeration<URL> classResources = shadowLoader.getResources("example/AltOnly.class");

            assertThat(altResource).isNotNull();
            assertThat(plainResource).isNotNull();
            assertThat(Collections.list(classResources)).isNotEmpty();
        }
    }

    @Test
    void skipsSelfLikeParentResourcesWhenOverrideClasspathIsPresent() throws Exception {
        Path selfBase = Files.createDirectory(temporaryDirectory.resolve("self-base"));
        Path overrideBase = Files.createDirectory(temporaryDirectory.resolve("override-base"));
        Path parentBase = Files.createDirectory(temporaryDirectory.resolve("parent-base"));
        write(parentBase.resolve("example/SelfLike.SCL." + SCL_SUFFIX), "shadow-resource");
        write(parentBase.resolve("META-INF/ShadowClassLoader"), SCL_SUFFIX + "\n");

        try (URLClassLoader parentLoader = urlClassLoader(parentBase)) {
            ClassLoader shadowLoader = newShadowClassLoader(parentLoader, selfBase);
            addOverrideClasspathEntry(shadowLoader, overrideBase);

            URL resource = shadowLoader.getResource("example/SelfLike.class");

            assertThat(resource).isNull();
        }
    }

    @Test
    void loadsClassesFromPrependedParentLoaders() throws Exception {
        Path selfBase = Files.createDirectory(temporaryDirectory.resolve("self-base"));
        ClassLoader prependedLoader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if ("example.FromPrepended".equals(name)) {
                    return String.class;
                }
                throw new ClassNotFoundException(name);
            }
        };

        Path parentBase = Files.createDirectory(temporaryDirectory.resolve("parent-base"));
        try (URLClassLoader parentLoader = urlClassLoader(parentBase)) {
            ClassLoader shadowLoader = newShadowClassLoader(parentLoader, selfBase);
            prependParent(shadowLoader, prependedLoader);

            Class<?> loadedClass = shadowLoader.loadClass("example.FromPrepended");

            assertThat(loadedClass).isSameAs(String.class);
        }
    }

    @Test
    void consultsAlreadyLoadedClassesWhenClassBytesHaveWrongName() throws Exception {
        Path selfBase = Files.createDirectory(temporaryDirectory.resolve("self-base"));
        writeClassBytes(selfBase.resolve("wrong/Name.SCL." + SCL_SUFFIX));

        Path parentBase = Files.createDirectory(temporaryDirectory.resolve("parent-base"));
        try (URLClassLoader parentLoader = urlClassLoader(parentBase)) {
            ClassLoader shadowLoader = newShadowClassLoader(parentLoader, selfBase);

            assertThatThrownBy(() -> shadowLoader.loadClass("wrong.Name"))
                    .satisfies(ShadowClassLoaderTest::assertWrongNameLinkageErrorOrUnsupportedDynamicLoading);
        }
    }

    private static ClassLoader newShadowClassLoader(ClassLoader parentLoader, Path selfBase) throws Exception {
        Class<?> shadowClassLoaderClass = Class.forName(SHADOW_CLASS_LOADER_NAME);
        Constructor<?> constructor = shadowClassLoaderClass.getDeclaredConstructor(
                ClassLoader.class,
                String.class,
                String.class,
                List.class,
                List.class
        );
        constructor.setAccessible(true);
        return (ClassLoader) constructor.newInstance(
                parentLoader,
                SCL_SUFFIX,
                selfBase.toString(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    private static void addOverrideClasspathEntry(ClassLoader shadowLoader, Path classpathEntry) throws Exception {
        Method method = shadowLoader.getClass().getMethod("addOverrideClasspathEntry", String.class);
        method.setAccessible(true);
        method.invoke(shadowLoader, classpathEntry.toString());
    }

    private static void prependParent(ClassLoader shadowLoader, ClassLoader prependedLoader) throws Exception {
        Method method = shadowLoader.getClass().getMethod("prependParent", ClassLoader.class);
        method.setAccessible(true);
        method.invoke(shadowLoader, prependedLoader);
    }

    private static URLClassLoader urlClassLoader(Path root) throws Exception {
        return new URLClassLoader(new URL[] {root.toUri().toURL()}, null);
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static void writeClassBytes(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, WRONG_NAME_CLASS_BYTES);
    }

    private static void assertWrongNameLinkageErrorOrUnsupportedDynamicLoading(Throwable throwable) {
        Throwable cause = throwable;
        if (throwable instanceof InvocationTargetException invocationTargetException) {
            cause = invocationTargetException.getCause();
        }
        if (cause instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        assertThat(cause)
                .isInstanceOf(LinkageError.class)
                .hasMessageContaining("wrong name");
    }
}
