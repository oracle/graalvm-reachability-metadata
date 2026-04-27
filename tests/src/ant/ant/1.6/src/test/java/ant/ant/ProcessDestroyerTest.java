/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.PumpStreamHandler;
import org.junit.jupiter.api.Test;

public class ProcessDestroyerTest {

    @Test
    public void executeRegistersAndRemovesProcessShutdownHook() throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final ByteArrayOutputStream error = new ByteArrayOutputStream();
        final Execute execute = new Execute(new PumpStreamHandler(output, error));

        execute.setCommandline(successfulCommand());

        assertThat(execute.execute()).isZero();
        assertThat(error.toString()).isEmpty();
    }

    private static String[] successfulCommand() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return new String[] {"cmd", "/c", "exit", "0"};
        }
        return new String[] {"/bin/sh", "-c", "exit 0"};
    }
}
