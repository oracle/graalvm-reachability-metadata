/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xmlParserAPIs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class SecuritySupport12Anonymous4Test {
    private static final String SECURITY_SUPPORT_CLASS = "javax.xml.parsers.SecuritySupport";
    private static final String RESOURCE_NAME = "xerces/xmlParserAPIs/security-support12-anonymous4-resource.txt";
    private static final String RESOURCE_CONTENT = "resource loaded by SecuritySupport12 anonymous action";
    private static final String SYSTEM_RESOURCE_NAME = "xerces/xmlParserAPIs/security-support12-system-resource.txt";
    private static final String SYSTEM_RESOURCE_CONTENT = "system resource loaded by SecuritySupport12 "
            + "anonymous action";

    @Test
    void getResourceAsStreamUsesProvidedClassLoaderInsidePrivilegedAction() throws Exception {
        try {
            RecordingResourceClassLoader resourceClassLoader = new RecordingResourceClassLoader();

            try (URLClassLoader xmlParserApisClassLoader = newXmlParserApisClassLoader();
                    InputStream stream = getResourceAsStream(
                            xmlParserApisClassLoader, resourceClassLoader, RESOURCE_NAME)) {
                assertThat(stream).isNotNull();
                assertThat(new String(stream.readAllBytes(), UTF_8)).isEqualTo(RESOURCE_CONTENT);
            }

            assertThat(resourceClassLoader.requestedResourceName).isEqualTo(RESOURCE_NAME);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void getResourceAsStreamUsesSystemResourceInsidePrivilegedActionWhenClassLoaderIsNull() throws Exception {
        try {
            try (URLClassLoader xmlParserApisClassLoader = newXmlParserApisClassLoader();
                    InputStream stream = getResourceAsStream(
                            xmlParserApisClassLoader, null, SYSTEM_RESOURCE_NAME)) {
                assertThat(stream).isNotNull();
                assertThat(new String(stream.readAllBytes(), UTF_8)).isEqualTo(SYSTEM_RESOURCE_CONTENT);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static InputStream getResourceAsStream(
            ClassLoader xmlParserApisClassLoader,
            ClassLoader resourceClassLoader,
            String resourceName) throws Exception {
        Object securitySupport12 = getSecuritySupport12(xmlParserApisClassLoader);
        Method getResourceAsStream = securitySupport12.getClass()
                .getDeclaredMethod("getResourceAsStream", ClassLoader.class, String.class);
        getResourceAsStream.setAccessible(true);
        return invokeForInputStream(getResourceAsStream, securitySupport12, resourceClassLoader, resourceName);
    }

    private static Object getSecuritySupport12(ClassLoader classLoader) throws Exception {
        Class<?> securitySupportClass = Class.forName(SECURITY_SUPPORT_CLASS, true, classLoader);
        Method getInstance = securitySupportClass.getDeclaredMethod("getInstance");
        getInstance.setAccessible(true);
        return invoke(getInstance, null);
    }

    private static URLClassLoader newXmlParserApisClassLoader() throws Exception {
        return new XmlParserApisClassLoader(
                new URL[] { xmlParserApisClassesLocation().toUri().toURL() },
                SecuritySupport12Anonymous4Test.class.getClassLoader());
    }

    private static Path xmlParserApisClassesLocation() throws Exception {
        Optional<Path> instrumentedClasses = findInstrumentedClasses();
        if (instrumentedClasses.isPresent()) {
            return instrumentedClasses.get();
        }

        String[] classPathEntries = System.getProperty("java.class.path", "").split(File.pathSeparator);
        for (String entry : classPathEntries) {
            Path path = Path.of(entry);
            Path fileName = path.getFileName();
            String normalizedEntry = entry.replace(File.separatorChar, '/');
            if (fileName != null
                    && fileName.toString().startsWith("xmlParserAPIs-")
                    && fileName.toString().endsWith(".jar")
                    && normalizedEntry.contains("/xerces/xmlParserAPIs/")) {
                return path;
            }
        }
        throw new IllegalStateException("xmlParserAPIs classes not found for the test class loader");
    }

    private static Optional<Path> findInstrumentedClasses() throws Exception {
        Optional<Path> buildDirectory = testProjectBuildDirectory();
        if (buildDirectory.isEmpty()) {
            return Optional.empty();
        }

        Path effectiveClassesRoot = buildDirectory.get().resolve(Path.of("jacoco", "effective"));
        if (!Files.isDirectory(effectiveClassesRoot)) {
            return Optional.empty();
        }
        try (Stream<Path> paths = Files.find(
                effectiveClassesRoot,
                5,
                (path, attributes) -> path.endsWith("javax/xml/parsers/SecuritySupport.class"))) {
            return paths.findFirst().map(path -> path.getParent().getParent().getParent().getParent());
        }
    }

    private static Optional<Path> testProjectBuildDirectory() throws Exception {
        URL codeSource = SecuritySupport12Anonymous4Test.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation();
        Path path = Path.of(codeSource.toURI());
        if (Files.isRegularFile(path)) {
            path = path.getParent();
        }

        for (Path current = path; current != null; current = current.getParent()) {
            Path fileName = current.getFileName();
            if (fileName != null && "build".equals(fileName.toString())) {
                return Optional.of(current);
            }
        }
        return Optional.empty();
    }

    private static InputStream invokeForInputStream(
            Method method, Object target, Object... arguments) throws Exception {
        return (InputStream) invoke(method, target, arguments);
    }

    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof Exception checkedException) {
                throw checkedException;
            }
            throw exception;
        }
    }

    private static final class XmlParserApisClassLoader extends URLClassLoader {
        private XmlParserApisClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("javax.xml.parsers.")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        try {
                            loadedClass = findClass(name);
                        } catch (ClassNotFoundException exception) {
                            loadedClass = super.loadClass(name, false);
                        }
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class RecordingResourceClassLoader extends ClassLoader {
        private String requestedResourceName;

        private RecordingResourceClassLoader() {
            super(null);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestedResourceName = name;
            if (RESOURCE_NAME.equals(name)) {
                return new ByteArrayInputStream(RESOURCE_CONTENT.getBytes(UTF_8));
            }
            return null;
        }
    }
}
