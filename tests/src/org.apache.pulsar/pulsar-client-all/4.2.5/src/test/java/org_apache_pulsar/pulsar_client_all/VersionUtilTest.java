/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.core.Version;
import org.apache.pulsar.shade.com.fasterxml.jackson.core.util.VersionUtil;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.cfg.MapperConfig;
import org.junit.jupiter.api.Test;

public class VersionUtilTest {

    @Test
    @SuppressWarnings("deprecation")
    void discoversPackageVersionAndHandlesAbsentMavenProperties() {
        final Version packageVersion = VersionUtil.versionFor(MapperConfig.class);
        final Version missingMavenVersion = VersionUtil.mavenVersionFor(
                MapperConfig.class.getClassLoader(), "example.missing", "no-artifact");

        assertThat(packageVersion.isUnknownVersion()).isFalse();
        assertThat(packageVersion.getGroupId()).isNotBlank();
        assertThat(packageVersion.getArtifactId()).isNotBlank();
        assertThat(missingMavenVersion.isUnknownVersion()).isTrue();
    }
}
