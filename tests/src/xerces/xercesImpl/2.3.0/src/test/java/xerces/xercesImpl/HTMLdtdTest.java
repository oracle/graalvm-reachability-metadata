/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xercesImpl;

import org.apache.xml.serialize.HTMLdtd;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class HTMLdtdTest {
    @Test
    void loadsHtmlEntitiesAndProvidesHtmlElementDefinitions() {
        Assertions.assertThat(HTMLdtd.charFromName("amp")).isEqualTo(38);
        Assertions.assertThat(HTMLdtd.fromChar(160)).isEqualTo("nbsp");
        Assertions.assertThat(HTMLdtd.isEmptyTag("IMG")).isTrue();
        Assertions.assertThat(HTMLdtd.isOptionalClosing("LI")).isTrue();
        Assertions.assertThat(HTMLdtd.isClosing("LI", "LI")).isTrue();
        Assertions.assertThat(HTMLdtd.isBoolean("INPUT", "checked")).isTrue();
    }
}
