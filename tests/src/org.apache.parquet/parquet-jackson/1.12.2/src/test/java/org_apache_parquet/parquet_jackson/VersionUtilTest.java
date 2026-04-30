/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import org.junit.jupiter.api.Test;
import shaded.parquet.com.fasterxml.jackson.core.Version;
import shaded.parquet.com.fasterxml.jackson.core.json.JsonReadContext;
import shaded.parquet.com.fasterxml.jackson.core.util.VersionUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilTest {
    @Test
    void versionForReadsPackageVersionFromShadedJacksonPackage() {
        Version version = VersionUtil.versionFor(JsonReadContext.class);

        assertThat(version).isNotNull();
        assertThat(version.isUnknownVersion()).isFalse();
        assertThat(version.getGroupId()).isEqualTo("shaded.parquet.com.fasterxml.jackson.core");
        assertThat(version.getArtifactId()).isEqualTo("jackson-core");
        assertThat(version.getMajorVersion()).isGreaterThan(0);
    }

    @SuppressWarnings("deprecation")
    @Test
    void mavenVersionForReadsPomPropertiesFromRuntimeClasspath() {
        ClassLoader classLoader = VersionUtilTest.class.getClassLoader();

        Version version = VersionUtil.mavenVersionFor(classLoader, "com.example.versionutil", "versionutil-fixture");

        assertThat(version).isNotNull();
        assertThat(version.isUnknownVersion()).isFalse();
        assertThat(version.toString()).isEqualTo("4.5.6-test");
        assertThat(version.getGroupId()).isEqualTo("com.example.versionutil");
        assertThat(version.getArtifactId()).isEqualTo("versionutil-fixture");
    }
}
