/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PropertyTest {
    private static final String CLASSPATH_RESOURCE = "property-task-resource.properties";
    private static final String CLASSPATH_PROPERTY = "property.loaded.from.classpath";
    private static final String SYSTEM_RESOURCE = "ant/ant/property-system-resource.properties";
    private static final String SYSTEM_PROPERTY = "property.loaded.from.system.resource";

    @Test
    void loadsPropertiesFromConfiguredClasspath(@TempDir Path temporaryDirectory) throws IOException {
        Files.writeString(
                temporaryDirectory.resolve(CLASSPATH_RESOURCE),
                CLASSPATH_PROPERTY + "=classpath-value\n",
                StandardCharsets.UTF_8);

        Property property = newPropertyTask(new Project());
        property.setClasspath(
                new org.apache.tools.ant.types.Path(property.getProject(), temporaryDirectory.toString()));
        property.setResource(CLASSPATH_RESOURCE);

        property.execute();

        assertThat(property.getProject().getProperty(CLASSPATH_PROPERTY)).isEqualTo("classpath-value");
    }

    @Test
    void loadsPropertiesFromSystemResourceWhenProjectDoesNotCreateClassLoader() {
        Property property = newPropertyTask(new NullClassLoaderProject());
        property.setClasspath(new org.apache.tools.ant.types.Path(property.getProject(), "unused"));
        property.setResource(SYSTEM_RESOURCE);

        property.execute();

        assertThat(property.getProject().getProperty(SYSTEM_PROPERTY)).isEqualTo("system-value");
    }

    private static Property newPropertyTask(Project project) {
        project.init();

        Property property = new Property();
        property.setProject(project);
        property.setTaskName("property");
        return property;
    }

    private static final class NullClassLoaderProject extends Project {
        @Override
        public AntClassLoader createClassLoader(org.apache.tools.ant.types.Path path) {
            return null;
        }
    }
}
