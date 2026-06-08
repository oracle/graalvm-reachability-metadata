/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testng.TestNG;
import org.testng.internal.PackageUtils;

public class PackageUtilsTest {
    private static final String PACKAGE_NAME = "org_testng.packageutilsfixture";
    private static final String PACKAGE_RESOURCE = "org_testng/packageutilsfixture/";

    @TempDir
    Path temporaryDirectory;

    @Test
    void findsClassesExposedByBundleResourceClassLoader() throws IOException {
        String originalTestClasspath = System.getProperty(TestNG.TEST_CLASSPATH);
        Path packageDirectory = Files.createDirectories(temporaryDirectory.resolve(PACKAGE_RESOURCE));
        Files.write(packageDirectory.resolve("BundleDiscoveredTest.class"), new byte[] {0});
        URL fileUrl = packageDirectory.toUri().toURL();
        URL bundleResourceUrl = new URL(null,
                "bundleresource://testng-reachability/" + PACKAGE_RESOURCE,
                new BundleResourceStreamHandler(fileUrl));

        PackageUtils.addClassLoader(new BundleResourceClassLoader(PACKAGE_RESOURCE, bundleResourceUrl));

        try {
            System.clearProperty(TestNG.TEST_CLASSPATH);

            String[] classes = PackageUtils.findClassesInPackage(PACKAGE_NAME, List.of(), List.of());

            assertThat(classes).containsExactly(PACKAGE_NAME + ".BundleDiscoveredTest");
        } finally {
            restoreProperty(TestNG.TEST_CLASSPATH, originalTestClasspath);
        }
    }

    private static void restoreProperty(String propertyName, String propertyValue) {
        if (propertyValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, propertyValue);
        }
    }

    private static final class BundleResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;

        private BundleResourceClassLoader(String resourceName, URL resourceUrl) {
            super(null);
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            if (resourceName.equals(name)) {
                return Collections.enumeration(List.of(resourceUrl));
            }
            return Collections.emptyEnumeration();
        }
    }

    private static final class BundleResourceStreamHandler extends URLStreamHandler {
        private final URL fileUrl;

        private BundleResourceStreamHandler(URL fileUrl) {
            this.fileUrl = fileUrl;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new BundleResourceConnection(url, fileUrl);
        }
    }

    public static final class BundleResourceConnection extends URLConnection {
        private final URL fileUrl;

        private BundleResourceConnection(URL url, URL fileUrl) {
            super(url);
            this.fileUrl = fileUrl;
        }

        @Override
        public void connect() {
        }

        public URL getFileURL() {
            return fileUrl;
        }
    }
}
