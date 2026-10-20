/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.http.util.VersionInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionInfoTest {
    @Test
    void searchesForPackageVersionDescriptors() {
        VersionInfo[] versions = VersionInfo.loadVersionInfo(
                new String[] {"org.apache.kylin.jdbc.shaded.org.apache.http"}, getClass().getClassLoader());

        assertThat(versions).doesNotContainNull();
        for (VersionInfo version : versions) {
            assertThat(version.getPackage()).isEqualTo("org.apache.kylin.jdbc.shaded.org.apache.http");
        }
    }
}
