/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.core.spi.scanning.PackageNamesScanner;
import com.sun.jersey.core.spi.scanning.ScannerListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PackageNamesScannerInnerResourcesProviderAnonymous1Test {
    @BeforeEach
    void useDefaultResourcesProvider() {
        PackageNamesScanner.setResourcesProvider(null);
    }

    @AfterEach
    void resetResourcesProvider() {
        PackageNamesScanner.setResourcesProvider(null);
    }

    @Test
    void defaultResourcesProviderDelegatesPackageLookupToSuppliedClassLoader() {
        final RecordingClassLoader classLoader = new RecordingClassLoader();
        final CountingScannerListener listener = new CountingScannerListener();
        final PackageNamesScanner scanner = new PackageNamesScanner(
                classLoader,
                new String[] {"missing.package.for.default.provider"});

        scanner.scan(listener);

        assertThat(classLoader.resourcesName).isEqualTo("missing/package/for/default/provider");
        assertThat(classLoader.invocations).isEqualTo(1);
        assertThat(listener.acceptedResources).isZero();
        assertThat(listener.processedResources).isZero();
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private String resourcesName;
        private int invocations;

        private RecordingClassLoader() {
            super(PackageNamesScannerInnerResourcesProviderAnonymous1Test.class.getClassLoader());
        }

        @Override
        public Enumeration<URL> getResources(final String name) throws IOException {
            resourcesName = name;
            invocations++;
            return Collections.emptyEnumeration();
        }
    }

    private static final class CountingScannerListener implements ScannerListener {
        private int acceptedResources;
        private int processedResources;

        @Override
        public boolean onAccept(final String name) {
            acceptedResources++;
            return true;
        }

        @Override
        public void onProcess(final String name, final InputStream in) throws IOException {
            processedResources++;
        }
    }
}
