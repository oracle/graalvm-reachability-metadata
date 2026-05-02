/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecuteJava;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.types.Path;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecuteJavaTest {
    private static final String INVOCATION_TARGET_CLASS_NAME = InvocationTarget.class.getName();

    @TempDir
    File temporaryDirectory;

    @Test
    void invokesMainClassLoadedByApplicationClassLoader() {
        String propertyName = propertyName("application");
        System.clearProperty(propertyName);

        try {
            ExecuteJava executeJava = executeJava(propertyName, "application-loader");

            executeJava.execute(newProject());

            assertThat(System.getProperty(propertyName)).isEqualTo("application-loader");
        } finally {
            System.clearProperty(propertyName);
        }
    }

    @Test
    void invokesMainClassLoadedThroughConfiguredAntClasspath() throws IOException {
        String propertyName = propertyName("ant-classpath");
        System.clearProperty(propertyName);

        try {
            Project project = newProject();
            ExecuteJava executeJava = executeJava(propertyName, "ant-classpath-loader");
            Path classpath = new Path(project);
            classpath.setLocation(copyInvocationTargetToTemporaryClasspath());
            executeJava.setClasspath(classpath);

            try {
                executeJava.execute(project);

                assertThat(System.getProperty(propertyName)).isEqualTo("ant-classpath-loader");
            } catch (BuildException exception) {
                rethrowUnlessUnsupportedDynamicClassLoading(exception);
            } catch (Error error) {
                rethrowUnlessUnsupportedDynamicClassLoading(error);
            }
        } finally {
            System.clearProperty(propertyName);
        }
    }

    private ExecuteJava executeJava(String propertyName, String propertyValue) {
        Commandline commandline = new Commandline();
        commandline.setExecutable(INVOCATION_TARGET_CLASS_NAME);
        commandline.createArgument().setValue(propertyName);
        commandline.createArgument().setValue(propertyValue);

        ExecuteJava executeJava = new ExecuteJava();
        executeJava.setJavaCommand(commandline);
        return executeJava;
    }

    private Project newProject() {
        Project project = new Project();
        project.init();
        return project;
    }

    private File copyInvocationTargetToTemporaryClasspath() throws IOException {
        String classResourceName = INVOCATION_TARGET_CLASS_NAME.replace('.', '/') + ".class";
        File classFile = new File(temporaryDirectory, classResourceName);
        assertThat(classFile.getParentFile().mkdirs()).isTrue();

        try (InputStream inputStream = ExecuteJavaTest.class.getClassLoader()
                .getResourceAsStream(classResourceName)) {
            assertThat(inputStream).isNotNull();
            Files.copy(inputStream, classFile.toPath());
        }
        return temporaryDirectory;
    }

    private String propertyName(String suffix) {
        return ExecuteJavaTest.class.getName() + "." + suffix;
    }

    private void rethrowUnlessUnsupportedDynamicClassLoading(BuildException exception) {
        Throwable cause = exception.getCause();
        if (!(cause instanceof Error error
                && NativeImageSupport.isUnsupportedFeatureError(error))) {
            throw exception;
        }
    }

    private void rethrowUnlessUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public static class InvocationTarget {
        public static void main(String[] arguments) {
            System.setProperty(arguments[0], arguments[1]);
        }
    }
}
