/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import org.jboss.util.propertyeditor.DocumentEditor;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocumentEditorTest {
    @Test
    void parsesAndSerializesAnXmlConfiguration() {
        DocumentEditor editor = new DocumentEditor();
        editor.setAsText("""
                <queue name="orders"><destination>east &amp; west</destination></queue>
                """);
        Document document = (Document) editor.getValue();
        Element root = document.getDocumentElement();
        assertEquals("queue", root.getTagName());
        assertEquals("orders", root.getAttribute("name"));
        assertEquals("east & west", root.getTextContent());

        String serialized = editor.getAsText();
        assertTrue(serialized.contains("east &amp; west"));
        DocumentEditor restored = new DocumentEditor();
        restored.setAsText(serialized);
        Document parsedAgain = (Document) restored.getValue();
        assertEquals("orders", parsedAgain.getDocumentElement().getAttribute("name"));
        assertEquals("east & west", parsedAgain.getDocumentElement().getTextContent());
    }
}
