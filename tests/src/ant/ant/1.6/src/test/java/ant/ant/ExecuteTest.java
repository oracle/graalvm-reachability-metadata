/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.junit.jupiter.api.Test;

public class ExecuteTest {
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
    void interpretsExitStatusUsingCurrentOperatingSystemConventions() {
        if (Os.isFamily("openvms")) {
            assertThat(Execute.isFailure(2)).isTrue();
            assertThat(Execute.isFailure(1)).isFalse();
        } else {
            assertThat(Execute.isFailure(1)).isTrue();
            assertThat(Execute.isFailure(0)).isFalse();
        }
    }
}
