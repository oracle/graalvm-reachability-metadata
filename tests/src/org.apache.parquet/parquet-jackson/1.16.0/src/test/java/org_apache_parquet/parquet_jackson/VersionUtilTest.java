/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.core.Version;
import shaded.parquet.com.fasterxml.jackson.core.json.JsonReadFeature;
import shaded.parquet.com.fasterxml.jackson.core.util.VersionUtil;

public class VersionUtilTest {
    @Test
    void versionForReadsJacksonPackageVersion() {
        final Version version = VersionUtil.versionFor(JsonReadFeature.class);

        assertThat(version).isNotNull();
        assertThat(version.isUnknownVersion()).isFalse();
        assertThat(version.getGroupId()).isEqualTo("shaded.parquet.com.fasterxml.jackson.core");
        assertThat(version.getArtifactId()).isEqualTo("jackson-core");
        assertThat(version.getMajorVersion()).isPositive();
    }

    @Test
    void mavenVersionForReadsPomPropertiesFromClassLoaderResource() {
        final ClassLoader classLoader = VersionUtilTest.class.getClassLoader();

        final Version version = VersionUtil.mavenVersionFor(
                classLoader, "org.apache.parquet.test", "versionutil-fixture");

        assertThat(version).isNotNull();
        assertThat(version.isUnknownVersion()).isFalse();
        assertThat(version.toString()).isEqualTo("4.5.6-fixture");
        assertThat(version.getGroupId()).isEqualTo("org.apache.parquet.test");
        assertThat(version.getArtifactId()).isEqualTo("versionutil-fixture");
    }
}
