/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jstl;

import java.util.Map;

import javax.servlet.jsp.jstl.tlv.PermittedTaglibsTLV;
import javax.servlet.jsp.tagext.ValidationMessage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PermittedTaglibsTLVTest {
    @Test
    void acceptsConfiguredLibrariesAndTheValidatingLibraryItself() {
        PermittedTaglibsTLV validator = new PermittedTaglibsTLV();
        validator.setInitParameters(Map.of("permittedTaglibs", "urn:example:format urn:example:core"));

        assertNull(validator.validate("policy", "urn:example:policy", new XmlPageData("""
                <jsp:root xmlns:jsp="http://java.sun.com/JSP/Page" version="2.0"
                          xmlns:policy="urn:example:policy"
                          xmlns:c="urn:example:core" xmlns:fmt="urn:example:format">
                    <c:out value="hello"/>
                </jsp:root>
                """)));
    }

    @Test
    void rejectsImportsOutsideTheConfiguredLibraries() {
        PermittedTaglibsTLV validator = new PermittedTaglibsTLV();
        validator.setInitParameters(Map.of("permittedTaglibs", "urn:example:core"));

        ValidationMessage[] messages = validator.validate("policy", "urn:example:policy", new XmlPageData("""
                <jsp:root xmlns:jsp="http://java.sun.com/JSP/Page" version="2.0"
                          xmlns:other="urn:example:unapproved">
                    <other:output/>
                </jsp:root>
                """));

        assertNotNull(messages);
        assertEquals(1, messages.length);
        assertTrue(messages[0].getMessage().contains("allows only"));
        assertTrue(messages[0].getMessage().contains("urn:example:core"));
    }
}
