/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.AntTypeDefinition;
import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectComponent;
import org.junit.jupiter.api.Test;

public class AntTypeDefinitionTest {

    @Test
    void createsRegisteredDataTypesUsingNoArgumentAndProjectConstructors() {
        Project project = new Project();
        ComponentHelper componentHelper = ComponentHelper.getComponentHelper(project);

        AntTypeDefinition noArgumentDefinition = newDefinition("noArgumentDataType", NoArgumentDataType.class);
        componentHelper.addDataTypeDefinition(noArgumentDefinition);

        NoArgumentDataType noArgumentDataType =
                (NoArgumentDataType) componentHelper.createDataType(noArgumentDefinition.getName());

        assertThat(noArgumentDataType.getProject()).isSameAs(project);

        AntTypeDefinition projectConstructorDefinition =
                newDefinition("projectConstructorDataType", ProjectConstructorDataType.class);
        projectConstructorDefinition.setClassLoader(ClassLoader.getSystemClassLoader());
        componentHelper.addDataTypeDefinition(projectConstructorDefinition);

        ProjectConstructorDataType projectConstructorDataType =
                (ProjectConstructorDataType) componentHelper.createDataType(projectConstructorDefinition.getName());

        assertThat(projectConstructorDataType.constructorProject).isSameAs(project);
        assertThat(projectConstructorDataType.getProject()).isSameAs(project);
    }

    private static AntTypeDefinition newDefinition(String name, Class<?> type) {
        AntTypeDefinition definition = new AntTypeDefinition();
        definition.setName(name);
        definition.setClassName(type.getName());
        return definition;
    }

    public static final class NoArgumentDataType extends ProjectComponent {
        public NoArgumentDataType() {
        }
    }

    public static final class ProjectConstructorDataType extends ProjectComponent {
        private final Project constructorProject;

        public ProjectConstructorDataType(Project constructorProject) {
            this.constructorProject = constructorProject;
        }
    }
}
