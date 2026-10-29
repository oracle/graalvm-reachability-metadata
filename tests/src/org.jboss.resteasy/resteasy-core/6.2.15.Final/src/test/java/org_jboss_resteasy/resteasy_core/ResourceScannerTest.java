/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import org.jboss.resteasy.core.scanner.ResourceScanner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceScannerTest {
    @Test
    void discoversApplicationsResourcesAndProvidersInClasspathIndex() throws Exception {
        ResourceScanner scanner = ResourceScanner.fromClassPath(Thread.currentThread().getContextClassLoader());
        assertThat(scanner.getApplications()).contains(ScannerFixture.IndexedApplication.class.getName());
        assertThat(scanner.getResources()).contains(ScannerFixture.IndexedResource.class.getName());
        assertThat(scanner.getProviders()).contains(ScannerFixture.IndexedProvider.class.getName());
    }
}
