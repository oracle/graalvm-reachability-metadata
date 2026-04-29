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
    public void loadVersionInfoReadsPackagedVersionProperties() {
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
    }

    @Test
    public void loadVersionInfoReturnsOnlyPackagesWithVersionProperties() {
        ClassLoader classLoader = VersionInfo.class.getClassLoader();

        VersionInfo[] versionInfos = VersionInfo.loadVersionInfo(new String[] {
                "org.apache.http",
                "org.apache.http.no_such_package"
        }, classLoader);

        assertThat(versionInfos).hasSize(1);
        assertThat(versionInfos[0].getPackage()).isEqualTo("org.apache.http");
        assertThat(versionInfos[0].getModule()).isEqualTo("httpcore");
    }
}
