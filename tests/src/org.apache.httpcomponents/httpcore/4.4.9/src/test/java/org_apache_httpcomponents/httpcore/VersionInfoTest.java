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

class VersionInfoTest {

    @Test
    void loadVersionInfoReadsPackagedVersionProperties() {
        ClassLoader classLoader = VersionInfo.class.getClassLoader();
        VersionInfo versionInfo = VersionInfo.loadVersionInfo("org.apache.http", classLoader);

        assertThat(versionInfo).isNotNull();
        assertThat(versionInfo.getPackage()).isEqualTo("org.apache.http");
        assertThat(versionInfo.getModule()).isEqualTo("httpcore");
        assertThat(versionInfo.getRelease()).isEqualTo("4.4.9");
        assertThat(versionInfo.getTimestamp()).isEqualTo(VersionInfo.UNAVAILABLE);
        assertThat(versionInfo.getClassloader()).isEqualTo(classLoader.toString());
    }

    @Test
    void getUserAgentUsesThePackagedRelease() {
        String userAgent = VersionInfo.getUserAgent("httpcore-test", "org.apache.http", VersionInfo.class);

        assertThat(userAgent)
                .isEqualTo("httpcore-test/4.4.9 (Java/" + System.getProperty("java.version") + ")");
    }
}
