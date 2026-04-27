/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecuteJava;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.types.Path;
import org.junit.jupiter.api.Test;

public class ExecuteJavaTest {
    @Test
    void invokesPublicMainThroughApplicationClassLoader() {
        String propertyName = getClass().getName() + ".application-loader";
        String propertyValue = "application invocation";
        ExecuteJava executeJava = executeJava(propertyName, propertyValue);

        assertExecutesMain(executeJava, new Project(), propertyName, propertyValue);
    }

    @Test
    void invokesPublicMainThroughProjectClassLoader() {
        String propertyName = getClass().getName() + ".project-loader";
        String propertyValue = "project classloader invocation";
        Project project = new DelegatingProject(RecordingMain.class);
        ExecuteJava executeJava = executeJava(propertyName, propertyValue);
        executeJava.setClasspath(new Path(project));

        assertExecutesMain(executeJava, project, propertyName, propertyValue);
    }

    private static ExecuteJava executeJava(String propertyName, String propertyValue) {
        Commandline commandline = new Commandline();
        commandline.setExecutable(RecordingMain.class.getName());
        commandline.addArguments(new String[] {propertyName, propertyValue});

        ExecuteJava executeJava = new ExecuteJava();
        executeJava.setJavaCommand(commandline);
        return executeJava;
    }

    private static void assertExecutesMain(ExecuteJava executeJava, Project project, String propertyName,
            String propertyValue) {
        System.clearProperty(propertyName);
        try {
            executeJava.execute(project);
            assertThat(System.getProperty(propertyName)).isEqualTo(propertyValue);
        } finally {
            System.clearProperty(propertyName);
        }
    }

    public static final class RecordingMain {
        private RecordingMain() {
        }

        public static void main(String[] args) {
            if (args.length != 2) {
                throw new IllegalArgumentException("Expected a property name and value");
            }
            System.setProperty(args[0], args[1]);
        }
    }

    private static final class DelegatingProject extends Project {
        private final Class<?> targetClass;

        private DelegatingProject(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        @Override
        public AntClassLoader createClassLoader(Path path) {
            return new DelegatingAntClassLoader(targetClass);
        }
    }

    private static final class DelegatingAntClassLoader extends AntClassLoader {
        private final Class<?> targetClass;

        private DelegatingAntClassLoader(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        @Override
        public Class<?> forceLoadClass(String classname) throws ClassNotFoundException {
            if (targetClass.getName().equals(classname)) {
                return targetClass;
            }
            return super.forceLoadClass(classname);
        }

        @Override
        protected synchronized Class<?> loadClass(String classname, boolean resolve) throws ClassNotFoundException {
            if (targetClass.getName().equals(classname)) {
                return targetClass;
            }
            return super.loadClass(classname, resolve);
        }
    }
}
