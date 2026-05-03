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
    void utilityReflectionHelpersInvokePublicMembers() throws Exception {
        Helper helper = new Helper("h2");

        assertThat(Utils.callStaticMethod("h2.UtilsTest.staticEcho", "H2")).isEqualTo("H2");
        assertThat(Utils.callMethod(helper, "upper")).isEqualTo("H2");
        assertThat(Utils.newInstance("h2.UtilsTest$Helper", "H2").toString()).isEqualTo("H2");
    }

    @Test
    void resourceLoaderReadsBundledWebResourceFromDataZip() throws Exception {
        byte[] bytes = Utils.getResource("/org/h2/server/web/res/helpTranslate.jsp");

        assertThat(bytes).isNotEmpty();
    }

    @Test
    void resourceLoaderScansBundledDataZipForMissingResource() throws Exception {
        assertThat(Utils.getResource("/missing/h2-resource.txt")).isNull();
    }

    public static String staticEcho(String value) {
        return value;
    }

    public static final class Helper {
        private final String value;

        public Helper(String value) {
            this.value = value;
        }

        public String upper() {
            return value.toUpperCase();
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
