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
import org.apache.tools.ant.types.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionHelperAnonymous10Test {
    @TempDir
    File temporaryDirectory;

    @Test
    void configuresPathAttributeThroughPublicAntIntrospection() {
        Project project = new Project();
        project.setBaseDir(temporaryDirectory);
        PathAttributeElement parent = new PathAttributeElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(
                project,
                PathAttributeElement.class);

        helper.setAttribute(project, parent, "runtimeclasspath", "lib/example.jar");

        assertThat(helper.getAttributeType("runtimeclasspath")).isSameAs(Path.class);
        assertThat(parent.runtimeClasspath.list())
                .containsExactly(new File(temporaryDirectory, "lib/example.jar").getAbsolutePath());
        assertThat(parent.runtimeClasspath.getProject()).isSameAs(project);
    }

    public static class PathAttributeElement {
        private Path runtimeClasspath;

        public void setRuntimeClasspath(Path runtimeClasspath) {
            this.runtimeClasspath = runtimeClasspath;
        }
    }
}
