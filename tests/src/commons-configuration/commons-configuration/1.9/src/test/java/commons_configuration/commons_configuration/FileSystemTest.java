/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration.DefaultFileSystem;
import org.apache.commons.configuration.FileSystem;
import org.junit.jupiter.api.Test;

public class FileSystemTest {
    @Test
    public void defaultFileSystemCanBeConfiguredWithSystemProperty() {
        FileSystem defaultFileSystem = FileSystem.getDefaultFileSystem();

        assertThat(defaultFileSystem).isInstanceOf(DefaultFileSystem.class);
    }
}
