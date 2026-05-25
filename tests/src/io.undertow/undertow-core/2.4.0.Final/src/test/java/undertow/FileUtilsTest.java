/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.util.FileUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileUtilsTest {

    @Test
    void readsResourceRelativeToProvidedClass() {
        String contents = FileUtils.readFile(FileUtilsTest.class, "file-utils-resource.txt");

        assertThat(contents).isEqualTo("FileUtils class-relative resource\n");
    }
}
