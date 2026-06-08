/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_core_client;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.artemis.core.version.Version;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.junit.jupiter.api.Test;

public class VersionLoaderTest {
    @Test
    void loadsBundledVersionPropertiesFromClasspathResource() {
        Version activeVersion = VersionLoader.getVersion();
        Version[] clientVersions = VersionLoader.getClientVersions();

        assertThat(clientVersions).isNotEmpty();
        assertThat(activeVersion).isSameAs(clientVersions[0]);
        assertThat(activeVersion.getVersionName()).isNotBlank();
        assertThat(activeVersion.getFullVersion()).contains(activeVersion.getVersionName());
        assertThat(activeVersion.getMajorVersion()).isPositive();
        assertThat(activeVersion.getMinorVersion()).isNotNegative();
        assertThat(activeVersion.getMicroVersion()).isNotNegative();
        assertThat(activeVersion.getIncrementingVersion()).isPositive();
    }
}
