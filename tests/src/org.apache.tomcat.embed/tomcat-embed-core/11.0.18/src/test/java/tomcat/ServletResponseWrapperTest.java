/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import jakarta.servlet.ServletResponseWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class ServletResponseWrapperTest {

    @Test
    void rejectsNullWrappedResponse() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ServletResponseWrapper(null));
    }
}
