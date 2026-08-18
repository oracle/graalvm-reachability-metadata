/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_el.commons_el;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.el.Constants;
import org.junit.jupiter.api.Test;

public class ConstantsTest {
    @Test
    public void loadsMessagesFromDefaultResourceBundle() {
        String message = Constants.getStringResource("NULL_EXPRESSION_STRING");

        assertThat(message)
                .contains("null expression string")
                .contains("expression evaluator");
        assertThat(Constants.NULL_EXPRESSION_STRING).isEqualTo(message);
    }
}
