/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.XmlProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Ant's XML property loading subsystem to make application configuration available to a build.
public class XmlConfigurationPropertyTest {
    @Test
    @Timeout(60)
    public void loadsApplicationConfigurationAsProjectProperties() throws IOException {
        Path workDirectory = Files.createTempDirectory("ant-xml-properties-");
        try {
            Path configuration = workDirectory.resolve("application.xml");
            Files.writeString(configuration, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <application name="inventory" environment="production">
                      <database host="db.internal" port="5432"/>
                      <feature name="audit" enabled="true">event-log</feature>
                    </application>
                    """, StandardCharsets.UTF_8);

            Project project = new Project();
            project.init();
            XmlProperty properties = new XmlProperty();
            properties.setProject(project);
            properties.setFile(configuration.toFile());
            properties.setPrefix("config");
            properties.setCollapseAttributes(true);
            properties.execute();

            assertEquals("inventory", project.getProperty("config.application.name"));
            assertEquals("production", project.getProperty("config.application.environment"));
            assertEquals("db.internal", project.getProperty("config.application.database.host"));
            assertEquals("5432", project.getProperty("config.application.database.port"));
            assertEquals("event-log", project.getProperty("config.application.feature"));
            assertEquals("audit", project.getProperty("config.application.feature.name"));
            assertEquals("true", project.getProperty("config.application.feature.enabled"));
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(XmlConfigurationPropertyTest::delete);
            }
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not clean up " + path, exception);
        }
    }
}
