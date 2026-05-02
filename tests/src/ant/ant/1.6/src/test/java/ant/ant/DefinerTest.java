/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.TaskAdapter;
import org.apache.tools.ant.taskdefs.Echo;
import org.apache.tools.ant.taskdefs.Typedef;
import org.apache.tools.ant.types.FileSet;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefinerTest {
    private static final String NAMED_DEFINITION = "definerNamedEcho";
    private static final String RESOURCE_DEFINITION = "definerResourceFileset";
    private static final String DEFINITIONS_RESOURCE = "ant/ant/definer-definitions.properties";

    @Test
    void definesNamedTypeWithAdapterAndAdaptToClasses() {
        Project project = newProject();
        Typedef typedef = newTypedef(project);
        typedef.setName(NAMED_DEFINITION);
        typedef.setClassname(Echo.class.getName());
        typedef.setAdapter(TaskAdapter.class.getName());
        typedef.setAdaptTo(Task.class.getName());

        if (executeAllowingNativeUnsupportedDynamicClassLoading(typedef)) {
            assertThat(componentHelper(project).getComponentClass(NAMED_DEFINITION)).isSameAs(Echo.class);
        }
    }

    @Test
    void definesTypesFromClasspathResource() {
        Project project = newProject();
        Typedef typedef = newTypedef(project);
        typedef.setResource(DEFINITIONS_RESOURCE);

        if (executeAllowingNativeUnsupportedDynamicClassLoading(typedef)) {
            assertThat(componentHelper(project).getComponentClass(RESOURCE_DEFINITION)).isSameAs(FileSet.class);
        }
    }

    private boolean executeAllowingNativeUnsupportedDynamicClassLoading(Typedef typedef) {
        try {
            typedef.execute();
            return true;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return false;
        }
    }

    private Typedef newTypedef(Project project) {
        Typedef typedef = new Typedef();
        typedef.setProject(project);
        typedef.setTaskName("typedef");
        typedef.init();
        return typedef;
    }

    private Project newProject() {
        Project project = new Project();
        project.init();
        return project;
    }

    private ComponentHelper componentHelper(Project project) {
        return ComponentHelper.getComponentHelper(project);
    }
}
