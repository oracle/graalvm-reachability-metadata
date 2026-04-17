/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package lombok.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MainTest {
    private final PrintStream systemOut = System.out;
    private final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

    @AfterEach
    void restoreSystemState() {
        System.setOut(systemOut);
        Thread.currentThread().setContextClassLoader(contextClassLoader);
    }

    @Test
    void delegatesToShadowLoadedCoreMainForHelpOutput() throws Throwable {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

        Main.main(new String[] {"--help"});

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("projectlombok.org")
                .contains("Other available commands:");
        assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(Main.getShadowClassLoader());
    }
}
