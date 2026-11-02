/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Discovers application configuration files with the Plexus directory scanner.
 *
 * A command-line application assembles its configuration from properties files below a
 * configuration directory. It includes files from tenant directories, excludes private
 * material, and reads the discovered files as application configuration.
 */
class ConfigurationInventoryTest {

    @Test
    void discoversPublicConfigurationFiles(@TempDir File workspace) throws Exception {
        FileUtils.forceMkdir(new File(workspace, "config/default"));
        FileUtils.forceMkdir(new File(workspace, "config/tenant"));
        FileUtils.forceMkdir(new File(workspace, "config/private"));
        FileUtils.forceMkdir(new File(workspace, "docs"));
        FileUtils.fileWrite(new File(workspace, "config/default/application.properties"),
                "feature.audit = enabled\n");
        FileUtils.fileWrite(new File(workspace, "config/tenant/eu.properties"),
                "region = eu\n");
        FileUtils.fileWrite(new File(workspace, "config/private/credentials.properties"),
                "password = do-not-load\n");
        FileUtils.fileWrite(new File(workspace, "docs/readme.txt"), "not configuration\n");

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(workspace);
        scanner.setIncludes(new String[] {"config/**/*.properties"});
        scanner.setExcludes(new String[] {"config/private/**"});
        scanner.scan();

        List<String> configurationFiles = Arrays.asList(scanner.getIncludedFiles());

        assertThat(configurationFiles)
                .containsExactlyInAnyOrder(
                        "config/default/application.properties",
                        "config/tenant/eu.properties");
        assertThat(configurationFiles)
                .allSatisfy(path -> assertThat(StringUtils.trim(FileUtils.fileRead(
                        new File(workspace, path)))).doesNotContain("do-not-load"));
    }
}
