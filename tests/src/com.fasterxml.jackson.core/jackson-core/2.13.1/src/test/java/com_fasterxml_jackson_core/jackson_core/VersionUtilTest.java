/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_core;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.util.VersionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilTest {
    @Test
    void versionForLoadsPackageVersionFromTheTargetPackage() {
        Version expectedVersion = new JsonFactory().version();

        Version version = VersionUtil.versionFor(JsonReadFeature.class);

        assertThat(version.isUnknownVersion()).isFalse();
        assertThat(version).isEqualTo(expectedVersion);
        assertThat(version.getGroupId()).isEqualTo("com.fasterxml.jackson.core");
        assertThat(version.getArtifactId()).isEqualTo("jackson-core");
    }

    @Test
    void mavenVersionForReadsPomPropertiesFromTheLibraryJar() {
        Version expectedVersion = new JsonFactory().version();

        Version version = VersionUtil.mavenVersionFor(
                VersionUtilTest.class.getClassLoader(),
                "com.fasterxml.jackson.core",
                "jackson-core"
        );

        assertThat(version.isUnknownVersion()).isFalse();
        assertThat(version).isEqualTo(expectedVersion);
    }
}
