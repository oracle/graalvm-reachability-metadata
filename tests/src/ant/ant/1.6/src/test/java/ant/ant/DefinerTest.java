/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import org.apache.tools.ant.AntTypeDefinition;
import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.TaskAdapter;
import org.apache.tools.ant.taskdefs.Typedef;
import org.junit.jupiter.api.Test;

public class DefinerTest {
    @Test
    void definesNamedTypeWithAdapterAndAdaptToClassesLoadedByName() {
        Project project = newProject();
        Typedef typedef = newTypedef(project, DefinerTest.class.getClassLoader());
        typedef.setName("runnable-component");
        typedef.setClassname(RunnableComponent.class.getName());
        typedef.setAdapter(TaskAdapter.class.getName());
        typedef.setAdaptTo(Runnable.class.getName());

        typedef.execute();

        AntTypeDefinition definition = ComponentHelper.getComponentHelper(project).getDefinition("runnable-component");
        assertThat(definition).isNotNull();
        assertThat(definition.getTypeClass(project)).isSameAs(RunnableComponent.class);
        assertThat(definition.getExposedClass(project)).isSameAs(RunnableComponent.class);
    }

    @Test
    void resolvesConfiguredResourceThroughAntlibClassLoader() {
        Project project = newProject();
        RecordingResourceClassLoader classLoader = new RecordingResourceClassLoader(DefinerTest.class.getClassLoader());
        Typedef typedef = newTypedef(project, classLoader);
        typedef.setResource("missing-definer-types.properties");

        typedef.execute();

        assertThat(classLoader.requestedResource).isEqualTo("missing-definer-types.properties");
    }

    private static Project newProject() {
        Project project = new Project();
        project.init();
        return project;
    }

    private static Typedef newTypedef(Project project, ClassLoader classLoader) {
        Typedef typedef = new Typedef();
        typedef.setProject(project);
        typedef.setTaskName("typedef");
        typedef.setAntlibClassLoader(classLoader);
        return typedef;
    }

    public static final class RunnableComponent implements Runnable {
        @Override
        public void run() {
        }
    }

    private static final class RecordingResourceClassLoader extends ClassLoader {
        private String requestedResource;

        private RecordingResourceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResource = name;
            return Collections.emptyEnumeration();
        }
    }
}
