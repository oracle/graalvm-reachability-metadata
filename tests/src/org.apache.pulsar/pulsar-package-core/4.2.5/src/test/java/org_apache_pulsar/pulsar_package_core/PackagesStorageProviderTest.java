/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_package_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.pulsar.packages.management.core.PackagesStorage;
import org.apache.pulsar.packages.management.core.PackagesStorageConfiguration;
import org.apache.pulsar.packages.management.core.PackagesStorageProvider;
import org.junit.jupiter.api.Test;

public class PackagesStorageProviderTest {

    @Test
    void createsProviderFromClassName() throws IOException {
        PackagesStorageProvider provider = PackagesStorageProvider.newProvider(
                TestPackagesStorageProvider.class.getName());

        assertThat(provider).isExactlyInstanceOf(TestPackagesStorageProvider.class);
    }

    public static class TestPackagesStorageProvider implements PackagesStorageProvider {
        public TestPackagesStorageProvider() {
        }

        @Override
        public PackagesStorage getStorage(PackagesStorageConfiguration config) {
            return null;
        }
    }
}
