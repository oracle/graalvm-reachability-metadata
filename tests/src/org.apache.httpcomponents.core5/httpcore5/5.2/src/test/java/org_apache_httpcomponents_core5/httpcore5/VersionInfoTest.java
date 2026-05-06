/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents_core5.httpcore5;

import org.apache.hc.core5.util.VersionInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionInfoTest {
    @Test
    void getSoftwareInfoLoadsPackageVersionResourceWhenAvailable() {
        final String softwareInfo = VersionInfo.getSoftwareInfo(
                "Apache-HttpCore",
                "org.apache.hc.core5",
                VersionInfo.class);

        assertThat(softwareInfo)
                .startsWith("Apache-HttpCore")
                .contains(" (Java/")
                .endsWith(")");
    }

    @Test
    void loadVersionInfoReturnsNullForPackageWithoutVersionResource() {
        final VersionInfo versionInfo = VersionInfo.loadVersionInfo(
                "org.apache.hc.core5.no.version.resource",
                VersionInfo.class.getClassLoader());

        assertThat(versionInfo).isNull();
    }
}
