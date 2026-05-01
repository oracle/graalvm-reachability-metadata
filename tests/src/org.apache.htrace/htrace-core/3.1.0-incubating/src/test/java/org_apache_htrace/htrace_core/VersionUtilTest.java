/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.core.JsonFactory;
import org.apache.htrace.fasterxml.jackson.core.Version;
import org.apache.htrace.fasterxml.jackson.core.json.JsonReadContext;
import org.apache.htrace.fasterxml.jackson.core.util.VersionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilTest {
    @Test
    void packageVersionForLoadsJsonPackageVersion() {
        Version discoveredVersion = VersionUtil.packageVersionFor(JsonReadContext.class);
        Version factoryVersion = new JsonFactory().version();

        assertThat(discoveredVersion).isNotNull();
        assertThat(discoveredVersion.isUknownVersion()).isFalse();
        assertThat(discoveredVersion).isEqualTo(factoryVersion);
    }

    @Test
    void versionForReadsVersionTextFromClassResource() {
        Version version = VersionUtil.versionFor(VersionUtilTest.class);

        assertThat(version).isNotNull();
        assertThat(version.isUknownVersion()).isFalse();
        assertThat(version.toString()).isEqualTo("9.8.7-test");
        assertThat(version.getGroupId()).isEqualTo("org.apache.htrace.versionutil");
        assertThat(version.getArtifactId()).isEqualTo("version-text-fixture");
    }

    @SuppressWarnings("deprecation")
    @Test
    void mavenVersionForReadsPomPropertiesFromClassLoaderResource() {
        Version version = VersionUtil.mavenVersionFor(
                VersionUtilTest.class.getClassLoader(),
                "org.apache.htrace.versionutil",
                "maven-version-fixture");

        assertThat(version).isNotNull();
        assertThat(version.isUknownVersion()).isFalse();
        assertThat(version.toString()).isEqualTo("6.5.4-fixture");
        assertThat(version.getGroupId()).isEqualTo("org.apache.htrace.versionutil");
        assertThat(version.getArtifactId()).isEqualTo("maven-version-fixture");
    }
}
