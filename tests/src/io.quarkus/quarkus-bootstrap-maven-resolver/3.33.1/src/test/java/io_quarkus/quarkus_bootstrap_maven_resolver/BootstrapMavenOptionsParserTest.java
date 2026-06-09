/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_maven_resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.quarkus.bootstrap.resolver.maven.options.BootstrapMavenOptions;
import io.quarkus.bootstrap.resolver.maven.options.BootstrapMavenOptionsParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BootstrapMavenOptionsParserTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesCommandLineOptionsProfilesAndUserProperties() throws Exception {
        Path userSettings = temporaryDirectory.resolve("settings.xml");
        Path pom = temporaryDirectory.resolve("pom.xml");
        Files.writeString(userSettings, "<settings/>");
        Files.writeString(pom, "<project/>");
        String commandLine = String.join(" ",
                "--offline",
                "--update-snapshots",
                "--batch-mode",
                "--no-transfer-progress",
                "--settings", userSettings.toString(),
                "--file", pom.toString(),
                "-DflagOnly",
                "-Dconfigured=value",
                "-Pdev,!prod");

        BootstrapMavenOptions options = BootstrapMavenOptions.newInstance(commandLine);
        Map<String, Object> parsed = BootstrapMavenOptions.parse(commandLine);
        Map<String, Object> parsedFromArray = BootstrapMavenOptionsParser.parse(commandLine.split(" "));

        assertTrue(parsed.containsKey(BootstrapMavenOptions.OFFLINE));
        assertTrue(parsedFromArray.containsKey(BootstrapMavenOptions.OFFLINE));
        assertTrue(options.hasOption(BootstrapMavenOptions.OFFLINE));
        assertTrue(options.hasOption(BootstrapMavenOptions.UPDATE_SNAPSHOTS));
        assertTrue(options.hasOption(BootstrapMavenOptions.BATCH_MODE));
        assertTrue(options.hasOption(BootstrapMavenOptions.NO_TRANSFER_PROGRESS));
        assertEquals(userSettings.toString(), options.getOptionValue(BootstrapMavenOptions.ALTERNATE_USER_SETTINGS));
        assertEquals(pom.toString(), options.getOptionValue(BootstrapMavenOptions.ALTERNATE_POM_FILE));
        assertTrue(options.getSystemProperties().containsKey("flagOnly"));
        assertEquals("value", options.getSystemProperties().getProperty("configured"));
        assertEquals(1, options.getActiveProfileIds().size());
        assertEquals("dev", options.getActiveProfileIds().get(0));
        assertEquals(1, options.getInactiveProfileIds().size());
        assertEquals("prod", options.getInactiveProfileIds().get(0));
    }

}
