/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2.dynamic;

import org.h2.util.Utils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest {
    @Test
    void loadsWebConsoleResourceFromPackagedResourceZip() throws IOException {
        byte[] resource = Utils.getResource("/org/h2/server/web/res/helpTranslate.jsp");

        assertThat(resource).isNotEmpty();
    }

    @Test
    void scalesValueForAvailableMemory() {
        assertThat(Utils.scaleForAvailableMemory(1024)).isGreaterThanOrEqualTo(0);
    }
}
