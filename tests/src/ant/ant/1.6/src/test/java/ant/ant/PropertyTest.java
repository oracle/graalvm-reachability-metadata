/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Property;
import org.apache.tools.ant.types.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyTest {
    private static final String RESOURCE_NAME = "ant/ant/property-resource.properties";
    private static final String PROPERTY_NAME = "property.resource.branch";
    private static final String PROPERTY_VALUE = "loaded";

    @Test
    void loadsResourceWithDefiningClassLoader() {
        Project project = newProject();
        Property property = newProperty(project);
        property.setResource(RESOURCE_NAME);

        property.execute();

        assertThat(project.getProperty(PROPERTY_NAME)).isEqualTo(PROPERTY_VALUE);
    }

    @Test
    void loadsResourceWithSystemClassLoaderWhenProjectProvidesNoClassLoader() {
        Project project = new NullClassLoaderProject();
        project.init();
        Property property = newProperty(project);
        property.setClasspath(new Path(project));
        property.setResource(RESOURCE_NAME);

        property.execute();

        assertThat(project.getProperty(PROPERTY_NAME)).isEqualTo(PROPERTY_VALUE);
    }

    private Property newProperty(Project project) {
        Property property = new Property();
        property.setProject(project);
        property.setTaskName("property");
        return property;
    }

    private Project newProject() {
        Project project = new Project();
        project.init();
        return project;
    }

    private static class NullClassLoaderProject extends Project {
        @Override
        public AntClassLoader createClassLoader(Path path) {
            return null;
        }
    }
}
