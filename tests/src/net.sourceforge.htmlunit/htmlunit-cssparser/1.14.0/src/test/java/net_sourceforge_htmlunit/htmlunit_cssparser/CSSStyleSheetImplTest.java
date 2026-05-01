/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_cssparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import com.gargoylesoftware.css.dom.AbstractCSSRuleImpl;
import com.gargoylesoftware.css.dom.CSSStyleSheetImpl;

public class CSSStyleSheetImplTest {
    @Test
    public void serializesAndDeserializesStyleSheetState() throws Exception {
        final CSSStyleSheetImpl styleSheet = new CSSStyleSheetImpl();
        styleSheet.setDisabled(true);
        styleSheet.setHref("https://example.invalid/assets/site.css");
        styleSheet.setMediaText("screen, print");
        styleSheet.setTitle("site styles");
        styleSheet.insertRule("body { color: blue; margin: 0; }", 0);

        final CSSStyleSheetImpl copy = serializeAndDeserialize(styleSheet);

        assertTrue(copy.getDisabled());
        assertEquals("https://example.invalid/assets/site.css", copy.getHref());
        assertEquals("screen, print", copy.getMedia().getMediaText());
        assertEquals("site styles", copy.getTitle());
        assertEquals(styleSheet.toString(), copy.toString());
        assertEquals(1, copy.getCssRules().getLength());

        final AbstractCSSRuleImpl copiedRule = copy.getCssRules().getRules().get(0);
        assertNotNull(copiedRule);
        assertSame(copy, copiedRule.getParentStyleSheet());
    }

    private CSSStyleSheetImpl serializeAndDeserialize(final CSSStyleSheetImpl styleSheet)
            throws IOException, ClassNotFoundException {
        final byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(styleSheet);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return assertInstanceOf(CSSStyleSheetImpl.class, input.readObject());
        }
    }
}
