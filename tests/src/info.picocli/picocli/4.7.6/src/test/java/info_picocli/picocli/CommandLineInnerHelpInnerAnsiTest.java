/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import org.junit.jupiter.api.Test;

import picocli.CommandLine.Help.Ansi;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerHelpInnerAnsiTest {

    @Test
    void autoAnsiEvaluatesTerminalWhenTtyModeIsRequested() {
        String previousAnsiMode = System.getProperty("picocli.ansi");
        System.setProperty("picocli.ansi", "tty");
        try {
            boolean enabled = Ansi.AUTO.enabled();
            String renderedText = Ansi.AUTO.string("@|bold terminal text|@");

            assertThat(renderedText).contains("terminal text");
            assertThat(renderedText).doesNotContain("@|bold").doesNotContain("|@");
            assertThat(Ansi.valueOf(enabled).enabled()).isEqualTo(enabled);
        } finally {
            if (previousAnsiMode == null) {
                System.clearProperty("picocli.ansi");
            } else {
                System.setProperty("picocli.ansi", previousAnsiMode);
            }
        }
    }
}
