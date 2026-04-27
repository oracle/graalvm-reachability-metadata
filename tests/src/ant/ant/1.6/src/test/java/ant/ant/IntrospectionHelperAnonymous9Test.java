/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IntrospectionHelperAnonymous9Test {
    @Test
    void setsFileAttributeThroughIntrospectionHelper(@TempDir Path temporaryDirectory) {
        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());
        FileAttributeElement element = new FileAttributeElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(project, FileAttributeElement.class);

        helper.setAttribute(project, element, "destination", "build/output.txt");

        assertThat(element.destination).isEqualTo(project.resolveFile("build/output.txt"));
    }

    public static final class FileAttributeElement {
        private File destination;

        public void setDestination(File destination) {
            this.destination = destination;
        }
    }
}
