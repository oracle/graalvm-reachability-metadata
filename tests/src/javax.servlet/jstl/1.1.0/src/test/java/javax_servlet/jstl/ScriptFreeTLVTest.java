/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jstl;

import java.util.Map;

import javax.servlet.jsp.jstl.tlv.ScriptFreeTLV;
import javax.servlet.jsp.tagext.ValidationMessage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScriptFreeTLVTest {
    private static final String SCRIPTED_PAGE = """
            <jsp:root xmlns:jsp="http://java.sun.com/JSP/Page" version="2.0">
                <jsp:declaration>int total;</jsp:declaration>
                <jsp:scriptlet>total++;</jsp:scriptlet>
                <jsp:expression>total</jsp:expression>
                <output value="%= total %"/>
            </jsp:root>
            """;

    @Test
    void acceptsScriptFreePages() {
        ScriptFreeTLV validator = new ScriptFreeTLV();
        assertNull(validator.validate("safe", "urn:example:safe", new XmlPageData("""
                <jsp:root xmlns:jsp="http://java.sun.com/JSP/Page" version="2.0">
                    <jsp:text>Hello</jsp:text>
                    <output value="${total}"/>
                </jsp:root>
                """)));
    }

    @Test
    void reportsEveryDisallowedScriptingCategory() {
        ScriptFreeTLV validator = new ScriptFreeTLV();
        ValidationMessage[] messages = validator.validate("safe", "urn:example:safe",
                new XmlPageData(SCRIPTED_PAGE));

        assertNotNull(messages);
        assertEquals(1, messages.length);
        String message = messages[0].getMessage();
        assertTrue(message.contains("1 declaration"));
        assertTrue(message.contains("1 scriptlet"));
        assertTrue(message.contains("1 expression"));
        assertTrue(message.contains("1 request-time attribute value"));
    }

    @Test
    void honorsExplicitScriptingPermissions() {
        ScriptFreeTLV validator = new ScriptFreeTLV();
        validator.setInitParameters(Map.of(
                "allowDeclarations", "true",
                "allowScriptlets", "true",
                "allowExpressions", "true",
                "allowRTExpressions", "true"));

        assertNull(validator.validate("safe", "urn:example:safe", new XmlPageData(SCRIPTED_PAGE)));
    }
}
