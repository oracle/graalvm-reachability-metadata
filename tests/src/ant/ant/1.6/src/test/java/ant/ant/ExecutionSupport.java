/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.ExecuteWatchdog;
import org.apache.tools.ant.taskdefs.PumpStreamHandler;
import org.apache.tools.ant.taskdefs.condition.Os;

public final class ExecutionSupport {
    private ExecutionSupport() {
    }

    public static void executeJavaVersion(File directory) throws Exception {
        File bin = new File(System.getProperty("java.home"), "bin");
        File java = new File(bin, Os.isFamily("windows") ? "java.exe" : "java");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExecuteWatchdog watchdog = new ExecuteWatchdog(30000);
        Execute execute = new Execute(new PumpStreamHandler(output, output), watchdog);
        execute.setAntRun(new Project());
        execute.setWorkingDirectory(directory);
        execute.setCommandline(new String[] {java.getAbsolutePath(), "-version"});
        assertEquals(0, execute.execute());
        assertFalse(watchdog.killedProcess());
        assertTrue(output.toString().contains("version"));
    }
}
