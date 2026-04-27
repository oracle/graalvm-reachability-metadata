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
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Available;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AvailableTest {
    private static final String PROJECT_CLASS_RESOURCE = "org/apache/tools/ant/Project.class";

    @Test
    void findsClassWithCurrentClassLoader() {
        Available available = newAvailableTask();
        available.setClassname(String.class.getName());

        assertThat(available.eval()).isTrue();
    }

    @Test
    void findsClassWithConfiguredClasspath(@TempDir Path temporaryDirectory) {
        Available available = newAvailableTask();
        available.setClasspath(classpath(available, temporaryDirectory));
        available.setClassname(Integer.class.getName());

        assertThat(available.eval()).isTrue();
    }

    @Test
    void findsResourceWithCurrentClassLoader() {
        Available available = newAvailableTask();
        available.setResource(PROJECT_CLASS_RESOURCE);

        assertThat(available.eval()).isTrue();
    }

    @Test
    void findsResourceWithConfiguredClasspath(@TempDir Path temporaryDirectory) throws IOException {
        String resourceName = "available-task-resource.txt";
        Files.writeString(temporaryDirectory.resolve(resourceName), "available", StandardCharsets.UTF_8);

        Available available = newAvailableTask();
        available.setClasspath(classpath(available, temporaryDirectory));
        available.setResource(resourceName);

        assertThat(available.eval()).isTrue();
    }

    private static org.apache.tools.ant.types.Path classpath(Available available, Path directory) {
        return new org.apache.tools.ant.types.Path(available.getProject(), directory.toString());
    }

    private static Available newAvailableTask() {
        Project project = new Project();
        project.init();

        Available available = new Available();
        available.setProject(project);
        available.setTaskName("available");
        return available;
    }
}
