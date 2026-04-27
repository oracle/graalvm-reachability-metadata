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

public class IntrospectionHelperAnonymous10Test {
    @Test
    void setsPathAttributeThroughIntrospectionHelper(@TempDir Path temporaryDirectory) {
        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());
        PathAttributeElement element = new PathAttributeElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(project, PathAttributeElement.class);

        helper.setAttribute(
                project,
                element,
                "classpath",
                "compile.jar" + File.pathSeparator + "runtime.jar");

        assertThat(element.classpath.list())
                .containsExactly(
                        project.resolveFile("compile.jar").getAbsolutePath(),
                        project.resolveFile("runtime.jar").getAbsolutePath());
    }

    public static final class PathAttributeElement {
        private org.apache.tools.ant.types.Path classpath;

        public void setClasspath(org.apache.tools.ant.types.Path classpath) {
            this.classpath = classpath;
        }
    }
}
