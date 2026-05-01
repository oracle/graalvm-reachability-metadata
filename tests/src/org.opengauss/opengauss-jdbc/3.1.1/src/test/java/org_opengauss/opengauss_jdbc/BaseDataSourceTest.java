/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.jdbc.AutoSave;
import org.postgresql.jdbc.PreferQueryMode;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseDataSourceTest {
    @Test
    void initializeFromCopiesSerializedBaseDataSourceState() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setServerName("db.example.test");
        source.setDatabaseName("sample_database");
        source.setUser("application_user");
        source.setPassword("application_password");
        source.setPortNumber(15432);
        source.setAllowEncodingChanges(true);
        source.setCharacterEncoding("UTF8");
        source.setConnectionExtraInfo(true);
        source.setApplicationName("base-data-source-test");
        source.setConnectTimeout(7);
        source.setAutosave(AutoSave.ALWAYS);
        source.setPreferQueryMode(PreferQueryMode.SIMPLE);

        PGSimpleDataSource copy = new PGSimpleDataSource();
        copy.initializeFrom(source);

        assertThat(copy.getServerName()).isEqualTo("db.example.test");
        assertThat(copy.getDatabaseName()).isEqualTo("sample_database");
        assertThat(copy.getUser()).isEqualTo("application_user");
        assertThat(copy.getPassword()).isEqualTo("application_password");
        assertThat(copy.getPortNumber()).isEqualTo(15432);
        assertThat(copy.getAllowEncodingChanges()).isTrue();
        assertThat(copy.getCharacterEncoding()).isEqualTo("UTF8");
        assertThat(copy.getConnectionExtraInfo()).isTrue();
        assertThat(copy.getApplicationName()).isEqualTo("base-data-source-test");
        assertThat(copy.getConnectTimeout()).isEqualTo(7);
        assertThat(copy.getAutosave()).isEqualTo(AutoSave.ALWAYS);
        assertThat(copy.getPreferQueryMode()).isEqualTo(PreferQueryMode.SIMPLE);
    }
}
