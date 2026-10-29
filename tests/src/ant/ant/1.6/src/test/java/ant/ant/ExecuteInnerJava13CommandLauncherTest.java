/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ExecuteInnerJava13CommandLauncherTest {
    @TempDir
    File directory;

    @Test
    void launchesSubprocessWithWorkingDirectory() throws Exception {
        ExecutionSupport.executeJavaVersion(directory);
    }
}
