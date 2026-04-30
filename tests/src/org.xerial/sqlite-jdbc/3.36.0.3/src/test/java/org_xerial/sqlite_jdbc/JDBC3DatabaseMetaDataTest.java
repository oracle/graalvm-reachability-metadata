/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial.sqlite_jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class JDBC3DatabaseMetaDataTest {
    @Test
    public void createsDatabaseMetadataAndReadsDriverDetails() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite::memory:");

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();

            assertThat(metadata.getDatabaseProductName()).isEqualTo("SQLite");
            assertThat(metadata.getDriverName()).isNotBlank();
            assertThat(metadata.getDriverVersion()).isNotBlank();
            assertThat(metadata.getDriverMajorVersion()).isGreaterThanOrEqualTo(0);
            assertThat(metadata.getDriverMinorVersion()).isGreaterThanOrEqualTo(0);
        }
    }
}
