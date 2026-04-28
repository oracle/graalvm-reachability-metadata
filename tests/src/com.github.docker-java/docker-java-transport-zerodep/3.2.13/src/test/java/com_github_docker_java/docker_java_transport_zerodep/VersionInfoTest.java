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
    private static final String CORE5_PACKAGE = "com.github.dockerjava.zerodep.shaded.org.apache.hc.core5";

    @Test
    void loadVersionInfoReadsShadedCoreVersionProperties() {
        ClassLoader classLoader = VersionInfo.class.getClassLoader();

        VersionInfo versionInfo = VersionInfo.loadVersionInfo(CORE5_PACKAGE, classLoader);

        assertThat(versionInfo).isNotNull();
        assertThat(versionInfo.getPackage()).isEqualTo(CORE5_PACKAGE);
        assertThat(versionInfo.getModule()).isEqualTo("httpcore5");
        assertThat(versionInfo.getRelease()).isNotEmpty().isNotEqualTo("${pom.version}");
        assertThat(versionInfo.getTimestamp()).isNotEmpty().isNotEqualTo("${mvn.timestamp}");
        assertThat(versionInfo.getClassloader()).isEqualTo(classLoader.toString());
        assertThat(versionInfo.toString()).contains(CORE5_PACKAGE, "httpcore5");
    }

    @Test
    void loadVersionInfoReturnsOnlyPackagesWithVersionProperties() {
        VersionInfo[] versionInfos = VersionInfo.loadVersionInfo(new String[] {
                CORE5_PACKAGE,
                "com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.missing"
        }, VersionInfo.class.getClassLoader());

        assertThat(versionInfos)
                .singleElement()
                .satisfies(versionInfo -> assertThat(versionInfo.getPackage()).isEqualTo(CORE5_PACKAGE));
    }

    @Test
    void getSoftwareInfoUsesAvailableVersionMetadata() {
        VersionInfo versionInfo = VersionInfo.loadVersionInfo(CORE5_PACKAGE, VersionInfo.class.getClassLoader());
        String softwareInfo = VersionInfo.getSoftwareInfo("docker-java-zerodep", CORE5_PACKAGE, VersionInfo.class);

        assertThat(versionInfo).isNotNull();
        String expectedNameAndRelease = VersionInfo.UNAVAILABLE.equals(versionInfo.getRelease())
                ? "docker-java-zerodep"
                : "docker-java-zerodep/" + versionInfo.getRelease();
        assertThat(softwareInfo)
                .isEqualTo(expectedNameAndRelease + " (Java/" + System.getProperty("java.version") + ")");
    }
}
