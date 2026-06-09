/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.util.VersionInfo;
import org.junit.jupiter.api.Test;

public class VersionInfoTest {
    @Test
    void versionInformationIsLoadedFromBundledProperties() {
        assertThat(VersionInfo.getVersion()).isNotBlank();
        assertThat(VersionInfo.getRevision()).isNotBlank();
        assertThat(VersionInfo.getBuildVersion()).contains(VersionInfo.getVersion());
    }
}
