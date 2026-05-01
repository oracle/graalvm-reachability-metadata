/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_core_asl;

import org.codehaus.jackson.Version;
import org.codehaus.jackson.util.VersionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilTest {
    @Test
    void versionForReadsVersionResourceFromClassPackage() {
        Version version = VersionUtil.versionFor(VersionUtilTest.class);

        assertThat(version).isNotNull();
        assertThat(version.isUknownVersion()).isFalse();
        assertThat(version.getMajorVersion()).isEqualTo(7);
        assertThat(version.getMinorVersion()).isEqualTo(8);
        assertThat(version.getPatchLevel()).isEqualTo(9);
        assertThat(version.toString()).isEqualTo("7.8.9-fixture");
    }
}
