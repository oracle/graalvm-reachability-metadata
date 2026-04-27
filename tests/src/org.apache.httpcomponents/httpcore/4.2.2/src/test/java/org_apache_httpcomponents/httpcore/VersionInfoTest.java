/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpcore;

import org.apache.http.util.VersionInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionInfoTest {

    @Test
    void loadVersionInfoReadsPackagedVersionProperties() {
        ClassLoader classLoader = VersionInfo.class.getClassLoader();

        VersionInfo versionInfo = VersionInfo.loadVersionInfo("org.apache.http", classLoader);

        assertThat(versionInfo).isNotNull();
        assertThat(versionInfo.getPackage()).isEqualTo("org.apache.http");
        assertThat(versionInfo.getModule()).isEqualTo("httpcore");
        assertThat(versionInfo.getRelease())
                .isNotBlank()
                .isNotEqualTo(VersionInfo.UNAVAILABLE)
                .doesNotContain("${");
        assertThat(versionInfo.getTimestamp()).isEqualTo(VersionInfo.UNAVAILABLE);
        assertThat(versionInfo.getClassloader()).isEqualTo(classLoader.toString());
        assertThat(versionInfo.toString()).contains("VersionInfo(org.apache.http:httpcore:");
    }

    @Test
    void loadVersionInfoReturnsNullWhenPackageHasNoVersionProperties() {
        ClassLoader classLoader = VersionInfo.class.getClassLoader();

        VersionInfo versionInfo = VersionInfo.loadVersionInfo("org.apache.http.missing", classLoader);

        assertThat(versionInfo).isNull();
    }
}
