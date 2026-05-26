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
    void initializesDataSourceFromSerializedBaseState() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setServerName("db.example.test");
        source.setDatabaseName("sample_database");
        source.setUser("sample_user");
        source.setPassword("sample_password");
        source.setPortNumber(5433);
        source.setAllowEncodingChanges(true);
        source.setCharacterEncoding("UTF-8");
        source.setConnectionExtraInfo(true);
        source.setApplicationName("metadata-coverage");
        source.setDefaultRowFetchSize(128);

        PGSimpleDataSource copy = new PGSimpleDataSource();
        copy.initializeFrom(source);

        assertThat(copy.getServerName()).isEqualTo("db.example.test");
        assertThat(copy.getDatabaseName()).isEqualTo("sample_database");
        assertThat(copy.getUser()).isEqualTo("sample_user");
        assertThat(copy.getPassword()).isEqualTo("sample_password");
        assertThat(copy.getPortNumber()).isEqualTo(5433);
        assertThat(copy.getAllowEncodingChanges()).isTrue();
        assertThat(copy.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(copy.getConnectionExtraInfo()).isTrue();
        assertThat(copy.getApplicationName()).isEqualTo("metadata-coverage");
        assertThat(copy.getDefaultRowFetchSize()).isEqualTo(128);
    }
}
