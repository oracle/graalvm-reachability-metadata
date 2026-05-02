/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.sql.Connection;
import java.sql.Driver;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.JDBCTask;
import org.apache.tools.ant.types.Path;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class JDBCTaskTest {
    private static final String DRIVER_INTERFACE_CLASS_NAME = Driver.class.getName();
    private static final String TEST_JDBC_URL = "jdbc:ant-test:unhandled";

    @Test
    void loadsDriverWithSystemClassLoader() {
        TestJDBCTask task = newConfiguredTask();
        task.setDriver(DRIVER_INTERFACE_CLASS_NAME);

        assertDriverInstantiationFailed(task);
    }

    @Test
    void loadsDriverThroughConfiguredAntClassLoader() {
        Project project = newProject();
        TestJDBCTask task = newConfiguredTask(project);
        task.setDriver(DRIVER_INTERFACE_CLASS_NAME);
        task.setCaching(false);
        task.setClasspath(new Path(project, System.getProperty("java.class.path")));

        assertDriverInstantiationFailedOrUnsupportedInNativeImage(task);
    }

    private TestJDBCTask newConfiguredTask() {
        return newConfiguredTask(newProject());
    }

    private TestJDBCTask newConfiguredTask(Project project) {
        TestJDBCTask task = new TestJDBCTask();
        task.setProject(project);
        task.setTaskName("jdbc-test");
        task.setUserid("user");
        task.setPassword("password");
        task.setUrl(TEST_JDBC_URL);
        return task;
    }

    private Project newProject() {
        Project project = new Project();
        project.init();
        return project;
    }

    private void assertDriverInstantiationFailed(TestJDBCTask task) {
        try {
            task.openConnection();
            fail("Expected JDBC driver instantiation to fail for the driver interface");
        } catch (BuildException exception) {
            assertThat(exception.getMessage()).contains("Instantiation Exception");
        }
    }

    private void assertDriverInstantiationFailedOrUnsupportedInNativeImage(TestJDBCTask task) {
        try {
            assertDriverInstantiationFailed(task);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class TestJDBCTask extends JDBCTask {
        Connection openConnection() {
            return getConnection();
        }
    }
}
