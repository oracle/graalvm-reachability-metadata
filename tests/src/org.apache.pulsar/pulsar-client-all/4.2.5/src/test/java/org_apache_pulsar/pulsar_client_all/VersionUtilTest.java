/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.core.Version;
import org.apache.pulsar.shade.com.fasterxml.jackson.core.json.JsonReadFeature;
import org.apache.pulsar.shade.com.fasterxml.jackson.core.util.VersionUtil;
import org.junit.jupiter.api.Test;

public class VersionUtilTest {
    @Test
    void discoversPackageAndMavenVersionMetadata() {
        Version packageVersion = VersionUtil.versionFor(JsonReadFeature.class);
        Version mavenVersion = VersionUtil.mavenVersionFor(
                JsonReadFeature.class.getClassLoader(),
                "com.fasterxml.jackson.core",
                "jackson-core");

        assertThat(packageVersion.isUnknownVersion()).isFalse();
        assertThat(packageVersion.getGroupId()).isEqualTo("com.fasterxml.jackson.core");
        assertThat(mavenVersion.isUnknownVersion()).isTrue();
    }
}
