/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.JDBCTask;
import org.apache.tools.ant.types.Path;
import org.junit.jupiter.api.Test;

public class JDBCTaskTest {
    @Test
    void attemptsToLoadConfiguredDriverWithSystemClassLoader() {
        ExposedJDBCTask task = newJDBCTask();
        task.setDriver(Project.class.getName());

        assertThatThrownBy(task::openConnection).isInstanceOf(ClassCastException.class);
    }

    @Test
    void attemptsToLoadConfiguredDriverWithAntClassLoader() {
        ExposedJDBCTask task = newJDBCTask();
        task.setDriver(Project.class.getName());
        task.setClasspath(new Path(task.getProject()));

        assertThatThrownBy(task::openConnection).isInstanceOf(ClassCastException.class);
    }

    private static ExposedJDBCTask newJDBCTask() {
        Project project = new Project();
        project.init();

        ExposedJDBCTask task = new ExposedJDBCTask();
        task.setProject(project);
        task.setTaskName("jdbc");
        task.setUrl("jdbc:unused");
        task.setUserid("user");
        task.setPassword("password");
        return task;
    }

    private static final class ExposedJDBCTask extends JDBCTask {
        private void openConnection() {
            getConnection();
        }
    }
}
