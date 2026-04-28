/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_core;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.VersionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilTest {
    @Test
    void versionForReadsPackageVersionFromClassPackage() {
        Version version = VersionUtil.versionFor(VersionUtilTest.class);

        assertThat(version).isNotNull();
        assertThat(version.isUknownVersion()).isFalse();
        assertThat(version.toString()).isEqualTo("1.2.3-test");
        assertThat(version.getGroupId()).isEqualTo("com.fasterxml.jackson.core");
        assertThat(version.getArtifactId()).isEqualTo("jackson-core-test");
    }

    @Test
    void parseVersionPreservesSnapshotGroupAndArtifact() {
        Version version = VersionUtil.parseVersion("4.5.6-SNAPSHOT", "com.example.versionutil", "versionutil-fixture");

        assertThat(version).isNotNull();
        assertThat(version.isUknownVersion()).isFalse();
        assertThat(version.toString()).isEqualTo("4.5.6-SNAPSHOT");
        assertThat(version.getGroupId()).isEqualTo("com.example.versionutil");
        assertThat(version.getArtifactId()).isEqualTo("versionutil-fixture");
    }

    @SuppressWarnings("deprecation")
    @Test
    void mavenVersionForReadsPomPropertiesFromClassLoaderResource() {
        ClassLoader classLoader = VersionUtilTest.class.getClassLoader();

        Version version = VersionUtil.mavenVersionFor(classLoader, "com.example.versionutil", "versionutil-fixture");

        assertThat(version).isNotNull();
        assertThat(version.isUknownVersion()).isFalse();
        assertThat(version.toString()).isEqualTo("4.5.6");
        assertThat(version.getGroupId()).isEqualTo("com.example.versionutil");
        assertThat(version.getArtifactId()).isEqualTo("versionutil-fixture");
    }
}
