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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.aesh.io.scanner.AnnotationDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AnnotationDetectorTest {
    private static final String SCANNED_PACKAGE = "org_aesh.aesh.annotationdetector";
    private static final String SCANNED_PACKAGE_PATH = "org_aesh/aesh/annotationdetector/";

    @Test
    void detectsPackageResourcesFromBundleURLConnection(@TempDir Path tempDir) throws Exception {
        Path jarPath = tempDir.resolve("annotated-resources.jar");
        writeJarWithPackageEntry(jarPath);
        URL jarUrl = new URL("jar:" + jarPath.toUri().toURL().toExternalForm() + "!/" + SCANNED_PACKAGE_PATH);
        BundleURLStreamHandler bundleURLStreamHandler = new BundleURLStreamHandler();
        URL bundleUrl = new URL(null, "bundle://aesh-test/" + SCANNED_PACKAGE_PATH, bundleURLStreamHandler);
        BundleResourceURLConnection bundleURLConnection = new BundleResourceURLConnection(bundleUrl, jarUrl);
        bundleURLStreamHandler.useConnection(bundleURLConnection);
        RecordingClassLoader classLoader = new RecordingClassLoader(bundleUrl);
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(classLoader);

            new AnnotationDetector(new RecordingTypeReporter()).detect(SCANNED_PACKAGE);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(classLoader.requestedResourceNames()).containsExactly(SCANNED_PACKAGE_PATH);
        assertThat(bundleURLConnection.localUrlRequested()).isTrue();
    }

    private static void writeJarWithPackageEntry(Path jarPath) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jarOutputStream.putNextEntry(new JarEntry(SCANNED_PACKAGE_PATH + "marker.txt"));
            jarOutputStream.write("not a class file".getBytes(StandardCharsets.UTF_8));
            jarOutputStream.closeEntry();
        }
    }

    public static final class BundleResourceURLConnection extends URLConnection {
        private final URL localUrl;
        private boolean localUrlRequested;

        private BundleResourceURLConnection(URL url, URL localUrl) {
            super(url);
            this.localUrl = localUrl;
        }

        @Override
        public void connect() {
            connected = true;
        }

        public URL getLocalURL() {
            localUrlRequested = true;
            return localUrl;
        }

        private boolean localUrlRequested() {
            return localUrlRequested;
        }
    }

    private static final class BundleURLStreamHandler extends URLStreamHandler {
        private BundleResourceURLConnection connection;

        @Override
        protected URLConnection openConnection(URL url) {
            return connection;
        }

        private void useConnection(BundleResourceURLConnection connection) {
            this.connection = connection;
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final URL packageUrl;
        private final ArrayList<String> requestedResourceNames = new ArrayList<>();

        private RecordingClassLoader(URL packageUrl) {
            super(AnnotationDetectorTest.class.getClassLoader());
            this.packageUrl = packageUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            requestedResourceNames.add(name);
            if (SCANNED_PACKAGE_PATH.equals(name)) {
                return Collections.enumeration(List.of(packageUrl));
            }
            return Collections.emptyEnumeration();
        }

        private List<String> requestedResourceNames() {
            return requestedResourceNames;
        }
    }

    private static final class RecordingTypeReporter implements AnnotationDetector.TypeReporter {
        @Override
        public void reportTypeAnnotation(Class<? extends Annotation> annotation, String className) {
        }

        @Override
        public Class[] annotations() {
            return new Class[] {Deprecated.class};
        }
    }
}
