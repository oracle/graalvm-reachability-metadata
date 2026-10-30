/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc;

import com.sun.tools.xjc.Language;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.reader.dtd.TDTDReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DtdMessagesTest {
    @Test
    void bindingForAnUndeclaredDtdElementIsRejected() {
        XjcTestSupport.Diagnostics diagnostics = new XjcTestSupport.Diagnostics();
        Options options = new Options();
        options.setSchemaLanguage(Language.DTD);
        Model model = TDTDReader.parse(
                XjcTestSupport.input("catalog.dtd", "<!ELEMENT catalog (#PCDATA)>"),
                XjcTestSupport.input("catalog.xjb", """
                        <xml-java-binding-schema>
                          <element name="absentEntry" type="class"/>
                        </xml-java-binding-schema>
                        """), diagnostics, options);
        assertNull(model);
        assertTrue(diagnostics.errors.stream().anyMatch(message -> message.contains("absentEntry")),
                diagnostics.errors.toString());
    }
}
