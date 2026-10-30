/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_sql;

import org.junit.jupiter.api.Test;
import org.springframework.boot.sql.autoconfigure.init.ApplicationScriptDatabaseInitializer;
import org.springframework.boot.sql.autoconfigure.init.SqlInitializationProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApplicationScriptDatabaseInitializerTest {
    @Test
    void suppliesOptionalDefaultScriptLocations() {
        DatabaseInitializationSettings settings = ApplicationScriptDatabaseInitializer.getSettings(
                new SqlInitializationProperties());

        assertEquals(List.of("optional:classpath*:schema-all.sql", "optional:classpath*:schema.sql"),
                settings.getSchemaLocations());
        assertEquals(List.of("optional:classpath*:data-all.sql", "optional:classpath*:data.sql"),
                settings.getDataLocations());
        assertEquals(DatabaseInitializationMode.EMBEDDED, settings.getMode());
        assertEquals(";", settings.getSeparator());
        assertFalse(settings.isContinueOnError());
        assertNull(settings.getEncoding());
    }

    @Test
    void expandsPlatformForBothKindsOfDefaultScripts() {
        SqlInitializationProperties properties = new SqlInitializationProperties();
        properties.setPlatform("h2");

        DatabaseInitializationSettings settings = ApplicationScriptDatabaseInitializer.getSettings(properties);

        assertEquals(List.of("optional:classpath*:schema-h2.sql", "optional:classpath*:schema.sql"),
                settings.getSchemaLocations());
        assertEquals(List.of("optional:classpath*:data-h2.sql", "optional:classpath*:data.sql"),
                settings.getDataLocations());
    }

    @Test
    void preservesExplicitLocationsAndExecutionOptions() {
        SqlInitializationProperties properties = new SqlInitializationProperties();
        properties.setSchemaLocations(List.of("file:database/schema.sql"));
        properties.setDataLocations(List.of("file:database/seed.sql", "optional:file:database/extra.sql"));
        properties.setPlatform("unused");
        properties.setMode(DatabaseInitializationMode.ALWAYS);
        properties.setContinueOnError(true);
        properties.setSeparator("@@");
        properties.setEncoding(StandardCharsets.UTF_16LE);

        DatabaseInitializationSettings settings = ApplicationScriptDatabaseInitializer.getSettings(properties);

        assertEquals(properties.getSchemaLocations(), settings.getSchemaLocations());
        assertEquals(properties.getDataLocations(), settings.getDataLocations());
        assertEquals(DatabaseInitializationMode.ALWAYS, settings.getMode());
        assertTrue(settings.isContinueOnError());
        assertEquals("@@", settings.getSeparator());
        assertEquals(StandardCharsets.UTF_16LE, settings.getEncoding());
    }

    @Test
    void emptyLocationsDisableDefaultScriptDiscovery() {
        SqlInitializationProperties properties = new SqlInitializationProperties();
        properties.setSchemaLocations(List.of());
        properties.setDataLocations(List.of());
        properties.setMode(DatabaseInitializationMode.NEVER);

        DatabaseInitializationSettings settings = ApplicationScriptDatabaseInitializer.getSettings(properties);

        assertEquals(List.of(), settings.getSchemaLocations());
        assertEquals(List.of(), settings.getDataLocations());
        assertEquals(DatabaseInitializationMode.NEVER, settings.getMode());
    }
}
