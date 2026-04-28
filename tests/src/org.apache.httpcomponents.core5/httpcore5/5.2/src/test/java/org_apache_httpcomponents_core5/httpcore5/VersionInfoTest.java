/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents_core5.httpcore5;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hc.core5.util.VersionInfo;
import org.junit.jupiter.api.Test;

public class VersionInfoTest {

    private static final String PACKAGE_NAME = "org.apache.hc.core5";
    private static final String MODULE_NAME = "httpcore5";
    private static final String RELEASE_NAME = "5.2";

    @Test
    void loadVersionInfoReadsVersionPropertiesFromLibraryJar() {
        final ClassLoader classLoader = VersionInfo.class.getClassLoader();

        final VersionInfo versionInfo = VersionInfo.loadVersionInfo(PACKAGE_NAME, classLoader);

        assertThat(versionInfo).isNotNull();
        assertThat(versionInfo.getPackage()).isEqualTo(PACKAGE_NAME);
        assertThat(versionInfo.getModule()).isEqualTo(MODULE_NAME);
        assertThat(versionInfo.getRelease()).isEqualTo(RELEASE_NAME);
        assertThat(versionInfo.getTimestamp()).isEqualTo(VersionInfo.UNAVAILABLE);
        assertThat(versionInfo.getClassloader()).isEqualTo(classLoader.toString());
        assertThat(versionInfo).hasToString(
                "VersionInfo(" + PACKAGE_NAME + ':' + MODULE_NAME + ':' + RELEASE_NAME + ")@" + classLoader);
    }
}
