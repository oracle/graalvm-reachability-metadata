/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.apache.velocity.test.IntrospectorTestCase2;
import org.graalvm.internal.tck.NativeImageSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IntrospectorTestCase2Test {
    private static final String TEST_CASE_CLASS_NAME = "org.apache.velocity.test.IntrospectorTestCase2";
    private static final String[] TEST_CASE_CLASS_RESOURCES = {
            "org/apache/velocity/test/IntrospectorTestCase2.class",
            "org/apache/velocity/test/IntrospectorTestCase2$Bar.class",
            "org/apache/velocity/test/IntrospectorTestCase2$Foo.class",
            "org/apache/velocity/test/IntrospectorTestCase2$Tester.class",
            "org/apache/velocity/test/IntrospectorTestCase2$Tester2.class",
            "org/apache/velocity/test/IntrospectorTestCase2$Woogie.class"
    };

    @org.junit.jupiter.api.Test
    void resolvesMostSpecificMethodAndRejectsAmbiguousOverload() {
        IntrospectorTestCase2 testCase = new IntrospectorTestCase2("IntrospectorTestCase2");

        testCase.runTest();
    }

    @org.junit.jupiter.api.Test
    void freshClassLoaderRunsWithEmptyCompilerClassCache() throws Exception {
        try {
            try (URLClassLoader classLoader = new IsolatedIntrospectorTestCase2ClassLoader(
                    new URL[] {materializeIntrospectorTestCase2Classes().toUri().toURL() },
                    IntrospectorTestCase2.class.getClassLoader())) {
                Class<?> testClass = classLoader.loadClass(TEST_CASE_CLASS_NAME);
                Test test = TestSuite.createTest(testClass, "runTest");
                TestResult result = new TestResult();

                test.run(result);

                assertEquals(0, result.errorCount(), Collections.list(result.errors()).toString());
                assertEquals(0, result.failureCount(), Collections.list(result.failures()).toString());
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Path materializeIntrospectorTestCase2Classes() throws IOException {
        Path tempDirectory = Files.createTempDirectory("velocity-introspector-testcase2-");
        Path velocityDepJar = null;

        for (String resourceName : TEST_CASE_CLASS_RESOURCES) {
            Path target = tempDirectory.resolve(resourceName);
            Files.createDirectories(target.getParent());

            InputStream classBytes = IntrospectorTestCase2.class.getClassLoader().getResourceAsStream(resourceName);
            if (classBytes == null) {
                if (velocityDepJar == null) {
                    velocityDepJar = findVelocityDepJar();
                }
                classBytes = openClassBytesFromJar(velocityDepJar, resourceName);
            }

            try (InputStream input = classBytes) {
                if (classBytes == null) {
                    throw new IOException("Missing class resource " + resourceName);
                }
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return tempDirectory;
    }

    private static Path findVelocityDepJar() throws IOException {
        Path cacheRoot = Path.of(System.getProperty("user.home"),
                ".gradle",
                "caches",
                "modules-2",
                "files-2.1",
                "velocity",
                "velocity-dep",
                "1.4");
        if (!Files.isDirectory(cacheRoot)) {
            throw new IOException("Cannot locate Gradle cache directory " + cacheRoot);
        }

        try (java.util.stream.Stream<Path> paths = Files.walk(cacheRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Cannot locate velocity-dep 1.4 jar under " + cacheRoot));
        }
    }

    private static InputStream openClassBytesFromJar(Path jarPath, String resourceName) throws IOException {
        ZipFile zipFile = new ZipFile(jarPath.toFile());
        ZipEntry zipEntry = zipFile.getEntry(resourceName);
        if (zipEntry == null) {
            zipFile.close();
            throw new IOException("Missing " + resourceName + " in " + jarPath);
        }

        return new FilterInputStream(zipFile.getInputStream(zipEntry)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    zipFile.close();
                }
            }
        };
    }

    private static final class IsolatedIntrospectorTestCase2ClassLoader extends URLClassLoader {
        private IsolatedIntrospectorTestCase2ClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (isIntrospectorTestCase2Class(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        loadedClass = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
            }
            return super.loadClass(name, resolve);
        }

        private static boolean isIntrospectorTestCase2Class(String name) {
            return name.equals(TEST_CASE_CLASS_NAME) || name.startsWith(TEST_CASE_CLASS_NAME + "$");
        }
    }
}
