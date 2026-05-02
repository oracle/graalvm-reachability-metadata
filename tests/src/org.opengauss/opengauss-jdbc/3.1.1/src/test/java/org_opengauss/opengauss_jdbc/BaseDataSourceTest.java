/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseDataSourceTest {

    @Test
    void initializeFromCopiesBaseDataSourceStateThroughObjectStreams() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setServerName("primary.example.test");
        source.setPortNumber(15432);
        source.setDatabaseName("sampledb");
        source.setUser("sample-user");
        source.setPassword("sample-password");
        source.setAllowEncodingChanges(true);
        source.setCharacterEncoding("UTF-8");
        source.setConnectionExtraInfo(true);
        source.setApplicationName("metadata-coverage");
        source.setConnectTimeout(7);
        source.setSsl(true);

        PGSimpleDataSource target = new PGSimpleDataSource();
        target.initializeFrom(source);

        assertThat(target.getServerName()).isEqualTo("primary.example.test");
        assertThat(target.getPortNumber()).isEqualTo(15432);
        assertThat(target.getDatabaseName()).isEqualTo("sampledb");
        assertThat(target.getUser()).isEqualTo("sample-user");
        assertThat(target.getPassword()).isEqualTo("sample-password");
        assertThat(target.getAllowEncodingChanges()).isTrue();
        assertThat(target.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(target.getConnectionExtraInfo()).isTrue();
        assertThat(target.getApplicationName()).isEqualTo("metadata-coverage");
        assertThat(target.getConnectTimeout()).isEqualTo(7);
        assertThat(target.getSsl()).isTrue();
    }
}
