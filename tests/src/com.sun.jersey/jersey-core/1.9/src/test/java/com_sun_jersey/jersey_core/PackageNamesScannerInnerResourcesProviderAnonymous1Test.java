/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.spi.scanning.PackageNamesScanner;
import com.sun.jersey.core.spi.scanning.ScannerListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class PackageNamesScannerInnerResourcesProviderAnonymous1Test {
    private static final String PACKAGE_NAME = "example.scanned.resources";
    private static final String PACKAGE_RESOURCE_NAME = "example/scanned/resources";
    private static final String RESOURCE_NAME = "marker.txt";
    private static final String RESOURCE_CONTENT = "package scanner marker";

    @TempDir
    private Path temporaryDirectory;

    @Test
    public void scansResourcesResolvedByDefaultResourcesProvider() throws Exception {
        final Path packageRoot = temporaryDirectory.resolve("package-root");
        Files.createDirectories(packageRoot);
        Files.write(packageRoot.resolve(RESOURCE_NAME), RESOURCE_CONTENT.getBytes(StandardCharsets.UTF_8));
        final SinglePackageResourceClassLoader classLoader = new SinglePackageResourceClassLoader(packageRoot);
        final RecordingScannerListener listener = new RecordingScannerListener();

        PackageNamesScanner.setResourcesProvider(null);
        try {
            final PackageNamesScanner scanner = new PackageNamesScanner(classLoader, new String[] {PACKAGE_NAME});
            scanner.scan(listener);

            assertThat(classLoader.getRequestedResourceNames()).containsExactly(PACKAGE_RESOURCE_NAME);
            assertThat(listener.getProcessedResources()).containsExactly(RESOURCE_NAME);
            assertThat(listener.getProcessedContent()).containsExactly(RESOURCE_CONTENT);
        } finally {
            PackageNamesScanner.setResourcesProvider(null);
        }
    }

    private static final class SinglePackageResourceClassLoader extends ClassLoader {
        private final Path packageRoot;
        private final List<String> requestedResourceNames = new ArrayList<String>();

        private SinglePackageResourceClassLoader(final Path packageRoot) {
            super(null);
            this.packageRoot = packageRoot;
        }

        @Override
        protected Enumeration<URL> findResources(final String name) throws IOException {
            requestedResourceNames.add(name);
            if (PACKAGE_RESOURCE_NAME.equals(name)) {
                return Collections.enumeration(Collections.singleton(packageRoot.toUri().toURL()));
            }
            return Collections.emptyEnumeration();
        }

        private List<String> getRequestedResourceNames() {
            return requestedResourceNames;
        }
    }

    private static final class RecordingScannerListener implements ScannerListener {
        private final List<String> processedResources = new ArrayList<String>();
        private final List<String> processedContent = new ArrayList<String>();

        @Override
        public boolean onAccept(final String name) {
            return RESOURCE_NAME.equals(name);
        }

        @Override
        public void onProcess(final String name, final InputStream in) throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[128];
            int length = in.read(buffer);
            while (length != -1) {
                out.write(buffer, 0, length);
                length = in.read(buffer);
            }
            processedResources.add(name);
            processedContent.add(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }

        private List<String> getProcessedResources() {
            return processedResources;
        }

        private List<String> getProcessedContent() {
            return processedContent;
        }
    }
}
