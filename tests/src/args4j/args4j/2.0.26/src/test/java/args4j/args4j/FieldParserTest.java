/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package args4j.args4j;

import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.FieldParser;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldParserTest {
    @Test
    void parsesAllBeanFieldsAsOptions() throws Exception {
        CliOptions options = new CliOptions();
        CmdLineParser parser = new CmdLineParser(null);

        new FieldParser().parse(parser, options);
        parser.parseArgument("-name", "args4j", "-count", "26");

        assertThat(options.name).isEqualTo("args4j");
        assertThat(options.count()).isEqualTo(26);
    }

    public static class BaseOptions {
        private int count;

        int count() {
            return count;
        }
    }

    public static class CliOptions extends BaseOptions {
        public String name;
    }
}
