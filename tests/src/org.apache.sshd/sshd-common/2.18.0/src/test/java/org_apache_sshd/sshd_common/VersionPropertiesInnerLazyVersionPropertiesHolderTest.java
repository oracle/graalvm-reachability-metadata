/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.sshd.common.config.VersionProperties;
import org.junit.jupiter.api.Test;

public class VersionPropertiesInnerLazyVersionPropertiesHolderTest {
    @Test
    void loadsVersionPropertiesFromTheLibraryResource() {
        assertThat(VersionProperties.getVersionProperties())
                .containsKeys("groupId", "artifactId", "version", "sshd-version");
        assertThat(VersionProperties.getVersionProperties().values())
                .allSatisfy(value -> assertThat(value).isNotBlank());
    }
}
