/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package args4j.args4j;

import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class MessagesTest {
    @Test
    void formatsUndefinedOptionMessageFromResourceBundle() {
        CmdLineParser parser = new CmdLineParser(null);

        assertThatExceptionOfType(CmdLineException.class)
                .isThrownBy(() -> parser.parseArgument("-missing"))
                .withMessageContaining("-missing")
                .withMessageContaining("not a valid option");
    }
}
