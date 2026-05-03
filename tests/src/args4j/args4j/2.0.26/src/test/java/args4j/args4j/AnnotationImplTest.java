/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package args4j.args4j;

import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.ConfigElement;
import org.kohsuke.args4j.spi.OptionImpl;
import org.kohsuke.args4j.spi.StringOptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationImplTest {
    @Test
    void resolvesConfiguredOptionHandlerClassByName() throws ClassNotFoundException {
        ConfigElement configElement = new ConfigElement();
        configElement.name = "-value";
        configElement.handler = StringOptionHandler.class.getName();
        configElement.aliases = new String[] {"--value"};
        configElement.metavar = "VALUE";
        configElement.multiValued = true;
        configElement.required = true;
        configElement.hidden = true;
        configElement.usage = "sets the value";

        OptionImpl option = new OptionImpl(configElement);

        assertThat(option.annotationType()).isEqualTo(Option.class);
        assertThat(option.name()).isEqualTo("-value");
        assertThat(option.handler()).isEqualTo(StringOptionHandler.class);
        assertThat(option.aliases()).containsExactly("--value");
        assertThat(option.metaVar()).isEqualTo("VALUE");
        assertThat(option.multiValued()).isTrue();
        assertThat(option.required()).isTrue();
        assertThat(option.hidden()).isTrue();
        assertThat(option.usage()).isEqualTo("sets the value");
    }
}
