/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.core.Version;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.core.util.VersionUtil;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.cfg.PackageVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilTest {
    @Test
    void discoversJacksonPackageAndMavenVersions() {
        Version packageVersion = VersionUtil.packageVersionFor(PackageVersion.class);
        Version mavenVersion = VersionUtil.mavenVersionFor(
                ObjectMapper.class.getClassLoader(),
                "org.apache.kylin",
                "kylin-jdbc");

        assertThat(packageVersion.isUknownVersion()).isFalse();
        assertThat(mavenVersion.isUknownVersion()).isFalse();
        assertThat(mavenVersion.getArtifactId()).isEqualTo("kylin-jdbc");
    }
}
