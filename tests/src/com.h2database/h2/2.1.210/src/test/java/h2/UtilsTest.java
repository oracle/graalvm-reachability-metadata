/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.util.Utils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest {
    @Test
    void looksUpResourceThroughUtilityResourceLoader() throws Exception {
        assertThat(Utils.getResource("/h2/missing-resource-for-coverage.txt")).isNull();
    }
}
