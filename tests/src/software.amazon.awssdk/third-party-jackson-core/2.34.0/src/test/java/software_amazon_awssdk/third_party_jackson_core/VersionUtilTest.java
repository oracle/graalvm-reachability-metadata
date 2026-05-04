/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.third_party_jackson_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.thirdparty.jackson.core.JsonFactory;
import software.amazon.awssdk.thirdparty.jackson.core.Version;
import software.amazon.awssdk.thirdparty.jackson.core.json.JsonReadFeature;
import software.amazon.awssdk.thirdparty.jackson.core.util.VersionUtil;

public class VersionUtilTest {
    @Test
    void versionForLoadsPackageVersionFromTargetPackage() {
        Version reflectedVersion = VersionUtil.versionFor(JsonReadFeature.class);

        assertThat(reflectedVersion).isEqualTo(new JsonFactory().version());
        assertThat(reflectedVersion.isUnknownVersion()).isFalse();
    }

    @Test
    void mavenVersionForReadsPomPropertiesFromClassLoaderResource() {
        String groupId = "example.group";
        String artifactId = "example-artifact";
        String version = "7.8.9-beta";
        ClassLoader classLoader = VersionUtilTest.class.getClassLoader();

        Version parsedVersion = VersionUtil.mavenVersionFor(classLoader, groupId, artifactId);

        assertThat(parsedVersion.getMajorVersion()).isEqualTo(7);
        assertThat(parsedVersion.getMinorVersion()).isEqualTo(8);
        assertThat(parsedVersion.getPatchLevel()).isEqualTo(9);
        assertThat(parsedVersion.isSnapshot()).isTrue();
        assertThat(parsedVersion.toString()).isEqualTo(version);
        assertThat(parsedVersion.getGroupId()).isEqualTo(groupId);
        assertThat(parsedVersion.getArtifactId()).isEqualTo(artifactId);
    }
}
