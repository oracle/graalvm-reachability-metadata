/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.filters.ReplaceTokens;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Ant's text filtering subsystem to render a deployable application configuration.
public class FilteredConfigurationGenerationTest {
    @Test
    @Timeout(60)
    public void rendersConfigurationTemplateWithApplicationValues() throws IOException {
        Path workDirectory = Files.createTempDirectory("ant-filtered-config-");
        try {
            Path template = workDirectory.resolve("application.properties.template");
            Files.writeString(template, "service.name=@service.name@\n"
                    + "release=@release@\n"
                    + "artifact=dist/@service.name@-@release@.zip\n", StandardCharsets.UTF_8);
            Path rendered = workDirectory.resolve("dist/application.properties");

            Project project = new Project();
            project.init();
            Copy copy = new Copy();
            copy.setProject(project);
            copy.setFile(template.toFile());
            copy.setTofile(rendered.toFile());
            copy.setEncoding(StandardCharsets.UTF_8.name());
            copy.setOutputEncoding(StandardCharsets.UTF_8.name());

            FilterChain filterChain = copy.createFilterChain();
            ReplaceTokens replaceTokens = new ReplaceTokens();
            addToken(replaceTokens, "service.name", "inventory");
            addToken(replaceTokens, "release", "2026.04");
            filterChain.addReplaceTokens(replaceTokens);
            copy.execute();

            assertEquals("service.name=inventory\nrelease=2026.04\n"
                    + "artifact=dist/inventory-2026.04.zip\n",
                    Files.readString(rendered, StandardCharsets.UTF_8));
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static void addToken(ReplaceTokens replaceTokens, String key, String value) {
        ReplaceTokens.Token token = new ReplaceTokens.Token();
        token.setKey(key);
        token.setValue(value);
        replaceTokens.addConfiguredToken(token);
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(FilteredConfigurationGenerationTest::delete);
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
