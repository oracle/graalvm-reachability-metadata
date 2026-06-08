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

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.junit.jupiter.api.Test;

public class FileChangedReloadingStrategyTest {
    @Test
    public void detectsModifiedConfigurationFile() throws Exception {
        Path file = Files.createTempFile("file-changed-reloading", ".properties");
        try {
            Files.write(file, "message=initial\n".getBytes(StandardCharsets.UTF_8));
            PropertiesConfiguration configuration = new PropertiesConfiguration(file.toFile());
            FileChangedReloadingStrategy strategy = new FileChangedReloadingStrategy();
            strategy.setConfiguration(configuration);
            strategy.setRefreshDelay(0);
            strategy.init();

            Files.write(file, "message=updated\n".getBytes(StandardCharsets.UTF_8));
            long modifiedTime = Math.max(file.toFile().lastModified() + 2_000, System.currentTimeMillis() + 2_000);
            assertThat(file.toFile().setLastModified(modifiedTime)).isTrue();

            assertThat(strategy.reloadingRequired()).isTrue();

            strategy.reloadingPerformed();

            assertThat(strategy.reloadingRequired()).isFalse();
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
