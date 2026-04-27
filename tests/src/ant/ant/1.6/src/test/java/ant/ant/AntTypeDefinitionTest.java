/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.AntTypeDefinition;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

public class AntTypeDefinitionTest {
    @Test
    void createsNoArgumentTypeLoadedByName() {
        Project project = new Project();
        AntTypeDefinition definition = new AntTypeDefinition();
        definition.setName("noArgumentComponent");
        definition.setClassName(NoArgumentComponent.class.getName());

        Object component = definition.create(project);

        assertThat(component).isInstanceOf(NoArgumentComponent.class);
        assertThat(definition.getTypeClass(project)).isSameAs(NoArgumentComponent.class);
    }

    @Test
    void createsProjectAwareTypeLoadedThroughConfiguredClassLoader() {
        Project project = new Project();
        RecordingClassLoader classLoader = new RecordingClassLoader(
                AntTypeDefinitionTest.class.getClassLoader(), ProjectAwareComponent.class.getName());
        AntTypeDefinition definition = new AntTypeDefinition();
        definition.setName("projectAwareComponent");
        definition.setClassName(ProjectAwareComponent.class.getName());
        definition.setClassLoader(classLoader);

        Object component = definition.create(project);

        assertThat(component).isInstanceOf(ProjectAwareComponent.class);
        assertThat(((ProjectAwareComponent) component).project).isSameAs(project);
        assertThat(classLoader.loadedClassName).isEqualTo(ProjectAwareComponent.class.getName());
    }

    public static final class NoArgumentComponent {
        public NoArgumentComponent() {
        }
    }

    public static final class ProjectAwareComponent {
        private final Project project;

        public ProjectAwareComponent(Project project) {
            this.project = project;
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final String expectedClassName;
        private String loadedClassName;

        private RecordingClassLoader(ClassLoader parent, String expectedClassName) {
            super(parent);
            this.expectedClassName = expectedClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (expectedClassName.equals(name)) {
                loadedClassName = name;
            }
            return super.loadClass(name);
        }
    }
}
