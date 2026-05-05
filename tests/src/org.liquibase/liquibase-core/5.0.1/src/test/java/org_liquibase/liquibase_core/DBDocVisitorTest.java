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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DBDocVisitorTest {

    @TempDir
    Path outputDirectory;

    @Test
    void generateDocumentationCopiesBundledDbDocResources() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:dbdoc;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            Database database = new H2Database();
            database.setConnection(new JdbcConnection(connection));

            try (Liquibase liquibase = new Liquibase("changelog.xml", new ClassLoaderResourceAccessor(), database)) {
                liquibase.update();

                liquibase.generateDocumentation(outputDirectory.toString());
            }
        }

        assertGeneratedResource("stylesheet.css");
        assertGeneratedResource("index.html");
        assertGeneratedResource("globalnav.html");
        assertGeneratedResource("overview-summary.html");
    }

    private void assertGeneratedResource(String fileName) throws Exception {
        Path generatedFile = outputDirectory.resolve(fileName);
        assertTrue(Files.isRegularFile(generatedFile), () -> "Expected generated dbdoc resource: " + generatedFile);
        assertTrue(Files.size(generatedFile) > 0, () -> "Expected non-empty dbdoc resource: " + generatedFile);
    }
}
