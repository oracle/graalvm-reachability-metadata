/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.tools.ant.Project;
import org.apache.velocity.texen.ant.TexenTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class TexenTaskTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsContextPropertiesFromClasspathWhenFileIsUnavailable() {
        TexenTask task = new TexenTask();
        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());
        task.setProject(project);

        task.setContextProperties("velocity/velocity_dep/texen-context.properties");

        ExtendedProperties contextProperties = task.getContextProperties();
        assertThat(contextProperties.getString("templateName")).isEqualTo("texen-classpath-template");
        assertThat(contextProperties.getString("outputName")).isEqualTo("generated.txt");
    }
}
