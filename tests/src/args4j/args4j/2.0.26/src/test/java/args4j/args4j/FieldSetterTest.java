/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package args4j.args4j;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.MapOptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldSetterTest {
    @Test
    void parsesOptionIntoPublicField() throws CmdLineException {
        PublicStringOptions options = new PublicStringOptions();

        new CmdLineParser(options).parseArgument("-name", "args4j");

        assertThat(options.name).isEqualTo("args4j");
    }

    @Test
    void parsesOptionIntoPrivateField() throws CmdLineException {
        PrivateStringOptions options = new PrivateStringOptions();

        new CmdLineParser(options).parseArgument("-name", "native-image");

        assertThat(options.name()).isEqualTo("native-image");
    }

    @Test
    void parsesMapOptionIntoPublicField() throws CmdLineException {
        PublicMapOptions options = new PublicMapOptions();

        new CmdLineParser(options).parseArgument("-property", "language=java");

        assertThat(options.properties).containsEntry("language", "java");
    }

    @Test
    void parsesMapOptionIntoPrivateField() throws CmdLineException {
        PrivateMapOptions options = new PrivateMapOptions();

        new CmdLineParser(options).parseArgument("-property", "runtime=native-image");

        assertThat(options.property("runtime")).isEqualTo("native-image");
    }

    public static class PublicStringOptions {
        @Option(name = "-name")
        public String name;
    }

    public static class PrivateStringOptions {
        @Option(name = "-name")
        private String name;

        String name() {
            return name;
        }
    }

    public static class PublicMapOptions {
        @Option(name = "-property", handler = MapOptionHandler.class)
        public Map<String, String> properties;
    }

    public static class PrivateMapOptions {
        @Option(name = "-property", handler = MapOptionHandler.class)
        private Map<String, String> properties = new HashMap<String, String>();

        String property(String key) {
            return properties.get(key);
        }
    }
}
