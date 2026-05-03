/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package args4j.args4j;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodSetterTest {
    @Test
    void parsesOptionIntoPrivateMethod() throws CmdLineException {
        PrivateMethodOptions options = new PrivateMethodOptions();

        new CmdLineParser(options).parseArgument("-tag", "alpha", "-tag", "beta");

        assertThat(options.tags()).containsExactly("alpha", "beta");
    }

    public static class PrivateMethodOptions {
        private final List<String> tags = new ArrayList<String>();

        @Option(name = "-tag")
        private void addTag(String tag) {
            tags.add(tag);
        }

        List<String> tags() {
            return tags;
        }
    }
}
