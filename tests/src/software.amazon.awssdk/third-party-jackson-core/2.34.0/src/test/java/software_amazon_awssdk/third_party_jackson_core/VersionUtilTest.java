/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.third_party_jackson_core;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.thirdparty.jackson.core.Version;
import software.amazon.awssdk.thirdparty.jackson.core.json.JsonReadFeature;
import software.amazon.awssdk.thirdparty.jackson.core.util.VersionUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilTest {
    @Test
    void versionForLoadsPackageVersionFromJacksonJsonPackage() {
        Version version = VersionUtil.versionFor(JsonReadFeature.class);

        assertThat(version).isNotNull();
        assertThat(version.isUnknownVersion()).isFalse();
        assertThat(version.getGroupId())
                .isEqualTo("software.amazon.awssdk.thirdparty.jackson.core");
        assertThat(version.getArtifactId()).isEqualTo("jackson-core");
        assertThat(version.getMajorVersion()).isPositive();
    }

    @SuppressWarnings("deprecation")
    @Test
    void mavenVersionForReadsPomPropertiesFromClassLoaderResources() {
        ClassLoader classLoader = VersionUtilTest.class.getClassLoader();

        Version version = VersionUtil.mavenVersionFor(
                classLoader,
                "com.example.versionutil",
                "versionutil-fixture");

        assertThat(version).isNotNull();
        assertThat(version.isUnknownVersion()).isFalse();
        assertThat(version.toString()).isEqualTo("4.5.6");
        assertThat(version.getGroupId()).isEqualTo("com.example.versionutil");
        assertThat(version.getArtifactId()).isEqualTo("versionutil-fixture");
    }
}
