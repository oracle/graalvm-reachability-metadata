/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_core;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.json.PackageVersion;
import com.fasterxml.jackson.core.util.VersionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilTest {
    @Test
    void versionForReadsGeneratedPackageVersion() {
        Version version = VersionUtil.versionFor(PackageVersion.class);
        Version packageVersion = PackageVersion.VERSION;

        assertThat(version).isNotNull();
        assertThat(version.isUknownVersion()).isFalse();
        assertThat(version.toString()).isEqualTo(packageVersion.toString());
        assertThat(version.getGroupId()).isEqualTo("com.fasterxml.jackson.core");
        assertThat(version.getArtifactId()).isEqualTo("jackson-core");
    }

    @Test
    void parseVersionKeepsSnapshotAndCoordinates() {
        Version version = VersionUtil.parseVersion("4.5.6-test", "com.example.versionutil", "versionutil-fixture");

        assertThat(version).isNotNull();
        assertThat(version.isUknownVersion()).isFalse();
        assertThat(version.isSnapshot()).isTrue();
        assertThat(version.toString()).isEqualTo("4.5.6-test");
        assertThat(version.toFullString()).isEqualTo("com.example.versionutil/versionutil-fixture/4.5.6-test");
    }

    @Test
    @SuppressWarnings("deprecation")
    void mavenVersionForReadsPomPropertiesFromClassLoaderResource() {
        Version version = VersionUtil.mavenVersionFor(
                VersionUtilTest.class.getClassLoader(), "com.example.versionutil", "versionutil-fixture");

        assertThat(version).isNotNull();
        assertThat(version.isUknownVersion()).isFalse();
        assertThat(version.toString()).isEqualTo("4.5.6");
        assertThat(version.getGroupId()).isEqualTo("com.example.versionutil");
        assertThat(version.getArtifactId()).isEqualTo("versionutil-fixture");
    }
}
