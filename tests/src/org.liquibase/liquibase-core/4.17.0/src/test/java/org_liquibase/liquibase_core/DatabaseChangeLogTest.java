/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Liquibase;
import liquibase.changelog.IncludeAllFilter;
import liquibase.database.Database;
import liquibase.database.core.H2Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseChangeLogTest {

    @Test
    void includeAllUsesConfiguredFilterAndComparator(
            @TempDir Path changelogDirectory) throws Exception {
        Path includedDirectory = Files.createDirectories(changelogDirectory.resolve("included"));
        writeChangelog(changelogDirectory.resolve("master.xml"), masterChangelog());
        writeChangelog(includedDirectory.resolve("a-insert.included.xml"),
                insertIncludedChangelog());
        writeChangelog(includedDirectory.resolve("m-skipped.xml"), skippedChangelog());
        writeChangelog(includedDirectory.resolve("z-create.included.xml"),
                createIncludedChangelog());

        String jdbcUrl = "jdbc:h2:mem:" + getClass().getSimpleName() + ";DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            Database database = new H2Database();
            database.setConnection(new JdbcConnection(connection));

            try (DirectoryResourceAccessor resourceAccessor =
                    new DirectoryResourceAccessor(changelogDirectory)) {
                try (Liquibase liquibase =
                        new Liquibase("master.xml", resourceAccessor, database)) {
                    liquibase.update();
                }
            }
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            assertThat(tableExists(connection, "include_all_item")).isTrue();
            assertThat(tableExists(connection, "skipped_include_all_item")).isFalse();
            assertThat(countRows(connection, "include_all_item")).isEqualTo(1);
        }
    }

    public static class OnlyIncludedXmlFilter implements IncludeAllFilter {
        @Override
        public boolean include(String changeLogPath) {
            return changeLogPath.endsWith(".included.xml");
        }
    }

    public static class ReversePathComparator implements Comparator<String> {
        @Override
        public int compare(String left, String right) {
            return right.compareTo(left);
        }
    }

    private static String masterChangelog() {
        String filterClassName = OnlyIncludedXmlFilter.class.getName();
        String comparatorClassName = ReversePathComparator.class.getName();
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog
                        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="
                                http://www.liquibase.org/xml/ns/dbchangelog
                                http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <includeAll path="included"
                            filter="%s"
                            resourceComparator="%s"/>
                </databaseChangeLog>
                """.formatted(filterClassName, comparatorClassName);
    }

    private static String createIncludedChangelog() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog
                        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="
                                http://www.liquibase.org/xml/ns/dbchangelog
                                http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="create-include-all-item" author="reachability-test">
                        <createTable tableName="include_all_item">
                            <column name="id" type="int">
                                <constraints primaryKey="true" nullable="false"/>
                            </column>
                            <column name="name" type="varchar(64)"/>
                        </createTable>
                    </changeSet>
                </databaseChangeLog>
                """;
    }

    private static String insertIncludedChangelog() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog
                        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="
                                http://www.liquibase.org/xml/ns/dbchangelog
                                http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="insert-include-all-item" author="reachability-test">
                        <insert tableName="include_all_item">
                            <column name="id" valueNumeric="1"/>
                            <column name="name" value="created-before-insert"/>
                        </insert>
                    </changeSet>
                </databaseChangeLog>
                """;
    }

    private static String skippedChangelog() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog
                        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="
                                http://www.liquibase.org/xml/ns/dbchangelog
                                http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="skipped-include-all-item" author="reachability-test">
                        <createTable tableName="skipped_include_all_item">
                            <column name="id" type="int"/>
                        </createTable>
                    </changeSet>
                </databaseChangeLog>
                """;
    }

    private static void writeChangelog(Path file, String content) throws Exception {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        String normalizedTableName = tableName.toUpperCase(Locale.ROOT);
        try (ResultSet resultSet = connection.getMetaData().getTables(
                null, null, normalizedTableName, new String[] {"TABLE"})) {
            return resultSet.next();
        }
    }

    private static int countRows(Connection connection, String tableName) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }
}
