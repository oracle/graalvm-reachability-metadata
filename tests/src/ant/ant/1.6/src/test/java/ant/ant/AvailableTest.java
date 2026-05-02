/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Available;
import org.apache.tools.ant.types.Path;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AvailableTest {
    private static final String PROJECT_CLASS_NAME = Project.class.getName();
    private static final String JDK_CLASS_NAME = String.class.getName();
    private static final String TEST_CLASS_RESOURCE = "ant/ant/AvailableTest.class";

    @Test
    void resolvesClassWithAvailableDefiningClassLoader() {
        Available available = newAvailable();
        available.setClassname(PROJECT_CLASS_NAME);

        assertThat(available.eval()).isTrue();
    }

    @Test
    void resolvesClassThroughConfiguredAntClasspath() {
        Project project = newProject();
        Available available = newAvailable(project);
        available.setClasspath(new Path(project));
        available.setClassname(PROJECT_CLASS_NAME);

        try {
            assertThat(available.eval()).isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void locatesResourceWithAvailableDefiningClassLoader() {
        Available available = newAvailable();
        available.setResource(TEST_CLASS_RESOURCE);

        assertThat(available.eval()).isTrue();
    }

    @Test
    void resolvesJdkClassForRuntimeClassLoaderBranch() {
        Available available = newAvailable();
        available.setClassname(JDK_CLASS_NAME);

        assertThat(available.eval()).isTrue();
    }

    private Available newAvailable() {
        return newAvailable(newProject());
    }

    private Available newAvailable(Project project) {
        Available available = new Available();
        available.setProject(project);
        available.setTaskName("available");
        return available;
    }

    private Project newProject() {
        Project project = new Project();
        project.init();
        return project;
    }
}
