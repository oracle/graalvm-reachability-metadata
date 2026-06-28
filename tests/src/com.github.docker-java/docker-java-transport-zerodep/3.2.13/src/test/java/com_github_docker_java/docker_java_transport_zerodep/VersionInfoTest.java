/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_docker_java.docker_java_transport_zerodep;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.util.VersionInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionInfoTest {
    @Test
    void loadsVersionPropertiesFromClassLoader() {
        VersionInfo versionInfo = VersionInfo.loadVersionInfo(
                "com.github.dockerjava.zerodep.shaded.org.apache.hc.core5",
                VersionInfoTest.class.getClassLoader());

        assertThat(versionInfo).isNotNull();
        assertThat(versionInfo.getPackage()).isEqualTo("com.github.dockerjava.zerodep.shaded.org.apache.hc.core5");
        assertThat(versionInfo.getModule()).isEqualTo("httpcore5");
        assertThat(versionInfo.getRelease()).isEqualTo("5.0.2");
        assertThat(versionInfo.getTimestamp()).isEqualTo("UNAVAILABLE");
        assertThat(versionInfo.toString()).contains("httpcore5", "5.0.2");
    }
}
