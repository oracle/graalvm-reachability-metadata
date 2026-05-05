/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package args4j.args4j.spi;

import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.spi.Messages;

import static org.assertj.core.api.Assertions.assertThat;

public class MessagesTest {
    @Test
    void formatsOptionHandlerMessageFromResourceBundle() {
        String message = Messages.ILLEGAL_BOOLEAN.format("truthy");

        assertThat(message)
                .contains("truthy")
                .isNotBlank();
    }
}
