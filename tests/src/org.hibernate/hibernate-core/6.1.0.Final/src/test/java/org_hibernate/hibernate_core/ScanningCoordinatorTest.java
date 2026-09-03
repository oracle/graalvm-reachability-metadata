/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.archive.scan.internal.ScanResultImpl;
import org.hibernate.boot.archive.scan.spi.ScanEnvironment;
import org.hibernate.boot.archive.scan.spi.ScanOptions;
import org.hibernate.boot.archive.scan.spi.ScanParameters;
import org.hibernate.boot.archive.scan.spi.ScanResult;
import org.hibernate.boot.archive.scan.spi.Scanner;
import org.hibernate.boot.archive.spi.ArchiveDescriptorFactory;
import org.hibernate.boot.archive.internal.StandardArchiveDescriptorFactory;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class ScanningCoordinatorTest {

    @Test
    public void buildsConfiguredScannersUsingEachSupportedConstructor() {
        FactoryConstructorScanner.instances.set(0);
        NoArgScanner.instances.set(0);

        bootstrapWithScanner(
                FactoryConstructorScanner.class,
                StandardArchiveDescriptorFactory.INSTANCE,
                "scanner-explicit-factory"
        );
        bootstrapWithScanner(FactoryConstructorScanner.class, null, "scanner-default-factory");
        bootstrapWithScanner(NoArgScanner.class, null, "scanner-no-arg");

        assertThat(FactoryConstructorScanner.instances).hasValue(2);
        assertThat(NoArgScanner.instances).hasValue(1);
    }

    private static void bootstrapWithScanner(
            Class<? extends Scanner> scanner,
            ArchiveDescriptorFactory archiveDescriptorFactory,
            String databaseName
    ) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(AvailableSettings.SCANNER, scanner);
        properties.put(AvailableSettings.URL, "jdbc:h2:mem:" + databaseName);
        properties.put(AvailableSettings.DRIVER, "org.h2.Driver");
        properties.put(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect");
        properties.put(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");
        if (archiveDescriptorFactory != null) {
            properties.put(AvailableSettings.SCANNER_ARCHIVE_INTERPRETER, archiveDescriptorFactory);
        }

        EntityManagerFactory factory =
                Persistence.createEntityManagerFactory("StudentPU", properties);
        try {
            assertThat(factory.isOpen()).isTrue();
        } finally {
            factory.close();
        }
    }

    public static class FactoryConstructorScanner implements Scanner {
        private static final AtomicInteger instances = new AtomicInteger();

        public FactoryConstructorScanner(ArchiveDescriptorFactory archiveDescriptorFactory) {
            assertThat(archiveDescriptorFactory).isNotNull();
            instances.incrementAndGet();
        }

        @Override
        public ScanResult scan(
                ScanEnvironment environment,
                ScanOptions options,
                ScanParameters parameters
        ) {
            return emptyResult();
        }
    }

    public static class NoArgScanner implements Scanner {
        private static final AtomicInteger instances = new AtomicInteger();

        public NoArgScanner() {
            instances.incrementAndGet();
        }

        @Override
        public ScanResult scan(
                ScanEnvironment environment,
                ScanOptions options,
                ScanParameters parameters
        ) {
            return emptyResult();
        }
    }

    private static ScanResult emptyResult() {
        return new ScanResultImpl(
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet()
        );
    }
}
