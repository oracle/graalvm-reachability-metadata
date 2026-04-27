/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.selectors.SizeSelector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IntrospectionHelperAnonymous11Test {
    @Test
    void setsEnumeratedAttributeThroughIntrospectionHelper(@TempDir Path temporaryDirectory) throws IOException {
        Project project = new Project();
        SizeSelector selector = new SizeSelector();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(project, SizeSelector.class);
        Path smallerFile = temporaryDirectory.resolve("smaller-file.txt");
        Path largerFile = temporaryDirectory.resolve("larger-file.txt");
        Files.write(smallerFile, new byte[] {1, 2, 3});
        Files.write(largerFile, new byte[] {1, 2, 3, 4, 5});

        selector.setValue(4L);
        helper.setAttribute(project, selector, "when", "less");

        assertThat(selector.isSelected(
                        temporaryDirectory.toFile(), smallerFile.getFileName().toString(), smallerFile.toFile()))
                .isTrue();
        assertThat(selector.isSelected(
                        temporaryDirectory.toFile(), largerFile.getFileName().toString(), largerFile.toFile()))
                .isFalse();
    }
}
