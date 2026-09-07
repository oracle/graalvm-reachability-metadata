/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.core.Version;
import org.testcontainers.shaded.com.fasterxml.jackson.core.util.VersionUtil;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.cfg.MapperConfig;
import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilTest {
    @Test
    void discoversPackageAndMavenVersionInformation() {
        Version mavenVersion = VersionUtil.mavenVersionFor(
            getClass().getClassLoader(),
            "example.missing",
            "version-util"
        );

        assertThat(mavenVersion.isUnknownVersion()).isTrue();
        assertThat(VersionUtil.packageVersionFor(MapperConfig.class).isUnknownVersion()).isFalse();
    }
}
