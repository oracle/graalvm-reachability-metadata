/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.JDBCTask;
import org.apache.tools.ant.types.Path;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class JDBCTaskTest {
    private static final String TEST_DRIVER_CLASS_NAME = NullJdbcDriver.class.getName();
    private static final String TEST_JDBC_URL = "jdbc:ant-test:unhandled";

    @Test
    void loadsDriverWithSystemClassLoader() {
        TestJDBCTask task = newConfiguredTask();
        task.setDriver(TEST_DRIVER_CLASS_NAME);

        assertDriverWasLoadedAndRejectedUrl(task);
    }

    @Test
    void loadsDriverThroughConfiguredAntClassLoader() {
        Project project = newProject();
        TestJDBCTask task = newConfiguredTask(project);
        task.setDriver(TEST_DRIVER_CLASS_NAME);
        task.setCaching(false);
        task.setClasspath(new Path(project, System.getProperty("java.class.path")));

        assertDriverWasLoadedAndRejectedUrlOrUnsupportedInNativeImage(task);
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

    private void assertDriverWasLoadedAndRejectedUrl(TestJDBCTask task) {
        try {
            task.openConnection();
            fail("Expected JDBC connection creation to fail after loading the test driver");
        } catch (BuildException exception) {
            assertThat(exception.getMessage()).contains("No suitable Driver");
        }
    }

    private void assertDriverWasLoadedAndRejectedUrlOrUnsupportedInNativeImage(TestJDBCTask task) {
        try {
            assertDriverWasLoadedAndRejectedUrl(task);
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

    public static final class NullJdbcDriver implements Driver {
        public NullJdbcDriver() {
        }

        public Connection connect(String url, Properties info) throws SQLException {
            return null;
        }

        public boolean acceptsURL(String url) throws SQLException {
            return true;
        }

        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return new DriverPropertyInfo[0];
        }

        public int getMajorVersion() {
            return 1;
        }

        public int getMinorVersion() {
            return 0;
        }

        public boolean jdbcCompliant() {
            return false;
        }

        public Logger getParentLogger() {
            return Logger.getLogger(NullJdbcDriver.class.getName());
        }
    }
}
