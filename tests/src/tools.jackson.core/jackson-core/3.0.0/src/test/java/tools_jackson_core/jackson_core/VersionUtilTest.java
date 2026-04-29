/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_core.jackson_core;

import tools.jackson.core.Version;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.VersionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilTest {

    @Test
    void versionForLoadsPackageVersionForJsonFactoryPackage() {
        Version discoveredVersion = VersionUtil.versionFor(JsonFactory.class);
        Version factoryVersion = new JsonFactory().version();

        assertThat(discoveredVersion.isUnknownVersion()).isFalse();
        assertThat(discoveredVersion).isEqualTo(factoryVersion);
    }
}
