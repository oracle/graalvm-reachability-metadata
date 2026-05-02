/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.File;
import java.io.IOException;

import org.apache.tools.ant.taskdefs.Execute;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessDestroyerTest {
    @Test
    void executeRegistersAndUnregistersShutdownHookAroundProcess() throws IOException {
        Execute execute = new Execute();
        execute.setCommandline(new String[] {javaExecutable(), "-version"});

        int exitCode = execute.execute();

        assertThat(exitCode).isZero();
        assertThat(execute.getExitValue()).isZero();
        assertThat(Execute.isFailure(exitCode)).isFalse();
    }

    private static String javaExecutable() {
        String executableName = isWindows() ? "java.exe" : "java";
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File executable = new File(new File(javaHome, "bin"), executableName);
            if (executable.isFile()) {
                return executable.getAbsolutePath();
            }
        }
        return executableName;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
