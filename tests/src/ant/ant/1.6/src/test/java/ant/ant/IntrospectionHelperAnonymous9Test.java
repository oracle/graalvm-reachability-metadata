/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.File;

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionHelperAnonymous9Test {
    @TempDir
    File temporaryDirectory;

    @Test
    void configuresFileAttributeThroughPublicAntIntrospection() {
        Project project = new Project();
        project.setBaseDir(temporaryDirectory);
        FileAttributeElement parent = new FileAttributeElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(
                project,
                FileAttributeElement.class);

        helper.setAttribute(project, parent, "destination", "build/output.txt");

        assertThat(helper.getAttributeType("destination")).isSameAs(File.class);
        assertThat(parent.destination)
                .isEqualTo(new File(temporaryDirectory, "build/output.txt").getAbsoluteFile());
    }

    public static class FileAttributeElement {
        private File destination;

        public void setDestination(File destination) {
            this.destination = destination;
        }
    }
}
