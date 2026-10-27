/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.core.H2Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class LiquibaseYamlChangeLogTest {

    @Test
    void updateBindsYamlCreateProcedurePathAndSqlFileSplitStatements() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:yamlChanges;DB_CLOSE_DELAY=-1")) {
            Database database = new H2Database();
            database.setConnection(new JdbcConnection(connection));

            Liquibase liquibase = new Liquibase(
                    "reporter-changelog.yaml",
                    new ClassLoaderResourceAccessor(),
                    database
            );
            liquibase.update();

            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM reporter_sql_file")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isZero();
            }
        }
    }
}
