/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_jopt_simple.jopt_simple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import org.junit.jupiter.api.Test;

public class MessagesTest {
    @Test
    void formatsParserExceptionMessagesFromTheResourceBundle() {
        OptionParser parser = new OptionParser();

        OptionException exception = catchThrowableOfType(
                () -> parser.parse("--unknown"),
                OptionException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains("unknown").contains("not a recognized option");
    }
}
