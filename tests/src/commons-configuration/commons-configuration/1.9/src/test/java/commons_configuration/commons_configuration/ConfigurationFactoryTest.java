/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.configuration.ConfigurationFactory;
import org.junit.jupiter.api.Test;

public class ConfigurationFactoryTest {
    @Test
    public void constructorWithConfigurationFileNameDerivesImplicitBasePath() throws Exception {
        Path directory = Files.createTempDirectory("configuration-factory");
        Path configurationFile = directory.resolve("factory.xml");
        Files.write(configurationFile, "<configuration/>".getBytes(StandardCharsets.UTF_8));
        try {
            ConfigurationFactory factory = new ConfigurationFactory(configurationFile.toString());

            assertThat(factory.getConfigurationFileName()).isEqualTo(configurationFile.getFileName().toString());
            assertThat(factory.getBasePath()).isEqualTo(directory.toFile().getAbsolutePath());
        } finally {
            Files.deleteIfExists(configurationFile);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void defaultConstructorUsesCurrentDirectoryBasePathUntilConfigurationIsSet() {
        ConfigurationFactory factory = new ConfigurationFactory();

        assertThat(factory.getBasePath()).isEqualTo(".");
    }
}
