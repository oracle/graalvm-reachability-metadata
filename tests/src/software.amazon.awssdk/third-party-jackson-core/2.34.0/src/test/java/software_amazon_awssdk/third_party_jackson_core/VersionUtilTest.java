/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.third_party_jackson_core;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.thirdparty.jackson.core.JsonFactory;
import software.amazon.awssdk.thirdparty.jackson.core.Version;
import software.amazon.awssdk.thirdparty.jackson.core.json.JsonReadFeature;
import software.amazon.awssdk.thirdparty.jackson.core.util.VersionUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilTest {
    @Test
    void discoversVersionForJsonConfigurationPackage() {
        Version version = VersionUtil.versionFor(JsonReadFeature.class);

        assertThat(version.isUnknownVersion()).isFalse();
        assertThat(version).isEqualTo(new JsonFactory().version());
        assertThat(version.getGroupId()).isNotBlank();
        assertThat(version.getArtifactId()).isEqualTo("jackson-core");
    }

    @SuppressWarnings("deprecation")
    @Test
    void readsApplicationVersionFromMavenProperties() {
        Version version = VersionUtil.mavenVersionFor(
                VersionUtilTest.class.getClassLoader(), "com.example.versioninfo", "application");

        assertThat(version.isUnknownVersion()).isFalse();
        assertThat(version.getGroupId()).isEqualTo("com.example.versioninfo");
        assertThat(version.getArtifactId()).isEqualTo("application");
        assertThat(version.getMajorVersion()).isEqualTo(7);
        assertThat(version.getMinorVersion()).isEqualTo(8);
        assertThat(version.getPatchLevel()).isEqualTo(9);
        assertThat(version.isSnapshot()).isTrue();
        assertThat(version.toString()).isEqualTo("7.8.9-SNAPSHOT");
    }
}
