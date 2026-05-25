/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.aesh.io.scanner.AnnotationDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AnnotationDetectorTest {
    private static final String SCANNED_PACKAGE = "org_aesh.aesh";
    private static final String SCANNED_RESOURCE_PATH = "org_aesh/aesh/";

    @Test
    void packageDetectionResolvesBundleUrlsToJarFiles(@TempDir Path tempDir) throws Exception {
        Path jarFile = tempDir.resolve("bundle-scanner-fixture.jar");
        writeJarWithIgnoredPackageResource(jarFile);

        URL jarPackageUrl = new URL(
                "jar:" + jarFile.toUri().toURL().toExternalForm() + "!/" + SCANNED_RESOURCE_PATH);
        URL bundlePackageUrl = new URL(
                null,
                "bundle://aesh-scanner-fixture/" + SCANNED_RESOURCE_PATH,
                new BundleUrlStreamHandler(jarPackageUrl));

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        RecordingTypeReporter reporter = new RecordingTypeReporter();
        Thread.currentThread().setContextClassLoader(new SinglePackageResourceClassLoader(
                originalClassLoader,
                SCANNED_RESOURCE_PATH,
                bundlePackageUrl));
        try {
            new AnnotationDetector(reporter).detect(SCANNED_PACKAGE);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(reporter.reportedClassNames()).isEmpty();
    }

    private static void writeJarWithIgnoredPackageResource(Path jarFile) throws IOException {
        Files.createDirectories(jarFile.getParent());
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
            JarEntry packageEntry = new JarEntry(SCANNED_RESOURCE_PATH);
            jarOutputStream.putNextEntry(packageEntry);
            jarOutputStream.closeEntry();

            JarEntry textEntry = new JarEntry(SCANNED_RESOURCE_PATH + "fixture.txt");
            jarOutputStream.putNextEntry(textEntry);
            jarOutputStream.write("not a class file".getBytes(StandardCharsets.UTF_8));
            jarOutputStream.closeEntry();
        }
    }

    private static final class RecordingTypeReporter implements AnnotationDetector.TypeReporter {
        private final Set<String> reportedClassNames = new LinkedHashSet<>();

        @Override
        public Class[] annotations() {
            return new Class[] {Deprecated.class};
        }

        @Override
        public void reportTypeAnnotation(Class<? extends Annotation> annotation, String className) {
            reportedClassNames.add(className);
        }

        Set<String> reportedClassNames() {
            return reportedClassNames;
        }
    }

    private static final class SinglePackageResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;

        SinglePackageResourceClassLoader(ClassLoader parent, String resourceName, URL resourceUrl) {
            super(parent);
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (resourceName.equals(name)) {
                return Collections.enumeration(Collections.singleton(resourceUrl));
            }
            return super.getResources(name);
        }
    }

    private static final class BundleUrlStreamHandler extends URLStreamHandler {
        private final URL localUrl;

        BundleUrlStreamHandler(URL localUrl) {
            this.localUrl = localUrl;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new BundleURLConnection(url, localUrl);
        }
    }

    public static final class BundleURLConnection extends URLConnection {
        private final URL localUrl;

        BundleURLConnection(URL url, URL localUrl) {
            super(url);
            this.localUrl = localUrl;
        }

        @Override
        public void connect() {
            connected = true;
        }

        public URL getLocalURL() {
            return localUrl;
        }
    }
}
