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
import liquibase.exception.ValidationFailedException;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class ValidationFailedExceptionTest {

    @Test
    void validateReportsDuplicateChangeSets(@TempDir Path changelogDirectory) throws Exception {
        Files.writeString(changelogDirectory.resolve("duplicate-changesets.xml"), duplicateChangeSetsChangelog(),
                StandardCharsets.UTF_8);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:validationfailed;DB_CLOSE_DELAY=-1")) {
            Database database = new H2Database();
            database.setConnection(new JdbcConnection(connection));

            try (DirectoryResourceAccessor resourceAccessor = new DirectoryResourceAccessor(changelogDirectory);
                    Liquibase liquibase = new Liquibase("duplicate-changesets.xml", resourceAccessor, database)) {
                assertThatExceptionOfType(ValidationFailedException.class)
                        .isThrownBy(liquibase::validate)
                        .satisfies(ValidationFailedExceptionTest::assertDuplicateChangeSetReport);
            }
        }
    }

    private static void assertDuplicateChangeSetReport(ValidationFailedException exception) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream printStream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            exception.printDescriptiveError(printStream);
        }

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("Validation Error:")
                .contains("1 changesets had duplicate identifiers")
                .contains("duplicate-id::reachability-test");
    }

    private static String duplicateChangeSetsChangelog() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog
                        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="
                                http://www.liquibase.org/xml/ns/dbchangelog
                                http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="duplicate-id" author="reachability-test">
                        <createTable tableName="validation_failed_item">
                            <column name="id" type="int"/>
                        </createTable>
                    </changeSet>
                    <changeSet id="duplicate-id" author="reachability-test">
                        <createTable tableName="validation_failed_other">
                            <column name="id" type="int"/>
                        </createTable>
                    </changeSet>
                </databaseChangeLog>
                """;
    }
}
