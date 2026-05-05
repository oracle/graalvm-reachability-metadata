/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_lang.commons_lang;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang.exception.NestableException;
import org.junit.jupiter.api.Test;

public class NestableDelegateTest {

    @Test
    public void nestedMessagesCanBeReadByIndex() {
        IllegalStateException root = new IllegalStateException("inner");
        NestableException exception = new NestableException("outer", root);

        String nestedMessage = exception.getMessage(1);

        assertThat(nestedMessage).isEqualTo("inner");
    }
}
