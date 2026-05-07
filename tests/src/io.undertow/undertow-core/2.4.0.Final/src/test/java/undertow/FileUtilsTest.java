/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.Version;
import io.undertow.util.FileUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileUtilsTest {

    @Test
    public void readsResourceRelativeToClass() {
        String versionProperties = FileUtils.readFile(Version.class, "version.properties");

        assertThat(versionProperties)
                .contains("undertow.version=")
                .doesNotContain("${project.version}");
    }
}
