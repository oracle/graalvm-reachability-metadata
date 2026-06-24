/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.util.VersionInfo;
import org.junit.jupiter.api.Test;

public class VersionInfoTest {
    @Test
    void staticAccessorsLoadCommonVersionResource() {
        String version = VersionInfo.getVersion();
        String revision = VersionInfo.getRevision();
        String branch = VersionInfo.getBranch();
        String date = VersionInfo.getDate();
        String user = VersionInfo.getUser();
        String url = VersionInfo.getUrl();
        String sourceChecksum = VersionInfo.getSrcChecksum();
        String protocVersion = VersionInfo.getProtocVersion();

        assertThat(version).isNotBlank();
        assertThat(revision).isNotBlank();
        assertThat(branch).isNotBlank();
        assertThat(date).isNotBlank();
        assertThat(user).isNotBlank();
        assertThat(url).isNotBlank();
        assertThat(sourceChecksum).isNotBlank();
        assertThat(protocVersion).isNotBlank();
        assertThat(VersionInfo.getBuildVersion())
                .contains(version, revision, user, sourceChecksum);
    }
}
