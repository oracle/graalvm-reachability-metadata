/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.archive.scan.internal.DisabledScanner;
import org.hibernate.boot.archive.scan.internal.StandardScanner;
import org.hibernate.boot.archive.scan.spi.ScanEnvironment;
import org.hibernate.boot.archive.scan.spi.Scanner;
import org.hibernate.boot.archive.spi.ArchiveDescriptor;
import org.hibernate.boot.archive.spi.ArchiveDescriptorFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScanningCoordinatorTest {

    @Test
    public void testCustomScannerClassWithConfiguredArchiveDescriptorFactory() {
        Map<String, Object> settings = scannerSettings(StandardScanner.class);
        settings.put(AvailableSettings.SCANNER_ARCHIVE_INTERPRETER, NoOpArchiveDescriptorFactory.INSTANCE);

        buildMetadata(settings);
    }

    @Test
    public void testCustomScannerClassUsesStandardArchiveDescriptorFactoryByDefault() {
        buildMetadata(scannerSettings(StandardScanner.class));
    }

    @Test
    public void testCustomScannerClassFallsBackToNoArgumentConstructor() {
        buildMetadata(scannerSettings(DisabledScanner.class));
    }

    private static Map<String, Object> scannerSettings(Class<? extends Scanner> scannerClass) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect");
        settings.put(AvailableSettings.SCANNER, scannerClass);
        return settings;
    }

    private static void buildMetadata(Map<String, Object> settings) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();
        try {
            new MetadataSources(registry)
                    .getMetadataBuilder()
                    .applyScanEnvironment(EmptyScanEnvironment.INSTANCE)
                    .build();
        }
        finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private enum NoOpArchiveDescriptorFactory implements ArchiveDescriptorFactory {
        INSTANCE;

        @Override
        public ArchiveDescriptor buildArchiveDescriptor(URL url) {
            return archiveContext -> {
                throw new UnsupportedOperationException("Archive visitation is not expected");
            };
        }

        @Override
        public ArchiveDescriptor buildArchiveDescriptor(URL url, String path) {
            return archiveContext -> {
                throw new UnsupportedOperationException("Archive visitation is not expected");
            };
        }

        @Override
        public URL getJarURLFromURLEntry(URL url, String entry) {
            return url;
        }
    }

    private enum EmptyScanEnvironment implements ScanEnvironment {
        INSTANCE;

        @Override
        public URL getRootUrl() {
            return null;
        }

        @Override
        public List<URL> getNonRootUrls() {
            return Collections.emptyList();
        }

        @Override
        public List<String> getExplicitlyListedClassNames() {
            return Collections.emptyList();
        }

        @Override
        public List<String> getExplicitlyListedMappingFiles() {
            return Collections.emptyList();
        }
    }
}
