/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_el.commons_el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.MissingResourceException;

import org.apache.commons.el.Constants;
import org.junit.jupiter.api.Test;

public class ConstantsTest {
    @Test
    public void loadsDefaultResourceBundleDuringConstantsInitialization() {
        assertThat(Constants.NULL_EXPRESSION_STRING)
                .isNotBlank();
        assertThat(Constants.getStringResource("PARSE_EXCEPTION"))
                .isNotBlank();
    }

    @Test
    public void reportsUnknownResourceKeys() {
        assertThatThrownBy(() -> Constants.getStringResource("UNKNOWN_RESOURCE_KEY_FOR_TEST"))
                .isInstanceOf(MissingResourceException.class)
                .hasMessageContaining("UNKNOWN_RESOURCE_KEY_FOR_TEST");
    }
}
