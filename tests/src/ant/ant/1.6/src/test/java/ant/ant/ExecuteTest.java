/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ExecuteTest {
    private static final String EXECUTE_CLASS_NAME = "org.apache.tools.ant.taskdefs.Execute";
    private static final String EXECUTE_RESOURCE_ROOT = "org/apache/tools/ant/taskdefs/Execute";
    private static final String[] EXECUTE_CLASS_RESOURCES = {
            "", "1", "CommandLauncher", "CommandLauncherProxy", "Java11CommandLauncher", "Java13CommandLauncher",
            "MacCommandLauncher", "OS2CommandLauncher", "PerlScriptCommandLauncher", "ScriptCommandLauncher",
            "VmsCommandLauncher", "WinNTCommandLauncher"};

    @Test
    void initializesLauncherAndPreservesExplicitNewEnvironment() {
        Execute execute = new Execute();
        String[] commandline = {"example-command", "example-argument"};
        String[] environment = {"EXECUTE_TEST_VARIABLE=explicit-value"};

        execute.setCommandline(commandline);
        execute.setEnvironment(environment);
        execute.setNewenvironment(true);

        assertThat(execute.getCommandline()).containsExactly(commandline);
        assertThat(execute.getEnvironment()).containsExactly(environment);
        assertThat(execute.getExitValue()).isEqualTo(Execute.INVALID);
        assertThat(execute.killedProcess()).isFalse();
    }

    @Test
    void initializesFreshlyLoadedExecuteClass(@TempDir Path temporaryDirectory) throws Exception {
        Path classesDirectory = temporaryDirectory.resolve("classes");
        copyExecuteClasses(classesDirectory);

        AntClassLoader loader = new AntClassLoader(null, false);
        loader.addPathElement(classesDirectory.toString());
        loader.addLoaderPackageRoot("org.apache.tools.ant.taskdefs");
        try {
            Class<?> executeClass = loader.forceLoadClass(EXECUTE_CLASS_NAME);

            AntClassLoader.initializeClass(executeClass);

            assertThat(executeClass.getName()).isEqualTo(EXECUTE_CLASS_NAME);
            assertThat(executeClass.getClassLoader()).isSameAs(loader);
        } finally {
            loader.cleanup();
        }
    }

    @Test
    void interpretsExitStatusUsingCurrentOperatingSystemConventions() {
        if (Os.isFamily("openvms")) {
            assertThat(Execute.isFailure(2)).isTrue();
            assertThat(Execute.isFailure(1)).isFalse();
        } else {
            assertThat(Execute.isFailure(1)).isTrue();
            assertThat(Execute.isFailure(0)).isFalse();
        }
    }

    private static void copyExecuteClasses(Path classesDirectory) throws IOException {
        for (String suffix : EXECUTE_CLASS_RESOURCES) {
            String resourceName = resourceName(suffix);
            Path classFile = classesDirectory.resolve(resourceName);
            Files.createDirectories(classFile.getParent());
            try (InputStream resource = Execute.class.getClassLoader().getResourceAsStream(resourceName)) {
                assertThat(resource).as(resourceName).isNotNull();
                Files.copy(resource, classFile);
            }
        }
    }

    private static String resourceName(String suffix) {
        if (suffix.isEmpty()) {
            return EXECUTE_RESOURCE_ROOT + ".class";
        }
        return EXECUTE_RESOURCE_ROOT + '$' + suffix + ".class";
    }
}
