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
import org.kohsuke.args4j.Option;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayFieldSetterTest {
    @Test
    void appendsRepeatedOptionValuesToArrayField() throws CmdLineException {
        ArrayOptions options = new ArrayOptions();

        new CmdLineParser(options).parseArgument("-include", "alpha", "-include", "beta");

        assertThat(options.includes).containsExactly("alpha", "beta");
    }

    public static class ArrayOptions {
        @Option(name = "-include")
        public String[] includes;
    }
}
