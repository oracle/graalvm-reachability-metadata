/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.codehaus.plexus.util.PropertyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads a command-line application's properties-file configuration.
 *
 * An application keeps its deployment settings in a properties file and uses Plexus Utils
 * to load the file before constructing the settings it exposes to the rest of the program.
 */
class PropertiesLoadingTest {

    @Test
    void loadsDeploymentSettings(@TempDir Path workspace) throws Exception {
        File configurationFile = writeConfiguration(workspace);

        Properties settings = PropertyUtils.loadProperties(configurationFile);

        assertThat(settings.getProperty("service.name")).isEqualTo("catalog");
        assertThat(settings.getProperty("service.port")).isEqualTo("8080");
        assertThat(settings.getProperty("feature.audit")).isEqualTo("true");
        assertThat(settings).doesNotContainKey("service.secret");
    }

    private File writeConfiguration(Path workspace) throws Exception {
        Path configurationFile = workspace.resolve("application.properties");
        Files.writeString(configurationFile, "service.name=catalog\n"
                + "service.port=8080\n"
                + "feature.audit=true\n");
        return configurationFile.toFile();
    }
}
