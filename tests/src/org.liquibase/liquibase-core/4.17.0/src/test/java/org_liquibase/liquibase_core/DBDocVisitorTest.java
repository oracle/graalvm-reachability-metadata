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
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class DBDocVisitorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void generateDocumentationCopiesBundledDbDocResources() throws Exception {
        Path changelog = tempDirectory.resolve("dbdoc-changelog.xml");
        Files.writeString(changelog, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog
                        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="
                                http://www.liquibase.org/xml/ns/dbchangelog
                                http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="1" author="dbdoc-test">
                        <createTable tableName="account">
                            <column name="id" type="int">
                                <constraints primaryKey="true" nullable="false"/>
                            </column>
                            <column name="name" type="varchar(64)"/>
                        </createTable>
                    </changeSet>
                </databaseChangeLog>
                """);

        Path outputDirectory = tempDirectory.resolve("dbdoc-output");
        Files.createDirectories(outputDirectory);

        try (Connection connection = DriverManager.getConnection(jdbcUrl());
                DirectoryResourceAccessor resourceAccessor =
                        new DirectoryResourceAccessor(tempDirectory)) {
            Database database = new H2Database();
            database.setConnection(new JdbcConnection(connection));

            String changelogFileName = changelog.getFileName().toString();
            Liquibase liquibase = new Liquibase(changelogFileName, resourceAccessor, database);
            liquibase.update();
            liquibase.generateDocumentation(outputDirectory.toString());
        }

        assertThat(outputDirectory.resolve("stylesheet.css")).exists().isRegularFile();
        assertThat(outputDirectory.resolve("globalnav.html")).exists().isRegularFile();
        assertThat(outputDirectory.resolve("overview-summary.html")).exists().isRegularFile();
    }

    private static String jdbcUrl() {
        return "jdbc:h2:mem:dbdoc-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }
}
