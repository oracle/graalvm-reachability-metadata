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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import com.gargoylesoftware.css.dom.AbstractCSSRuleImpl;
import com.gargoylesoftware.css.dom.CSSMediaRuleImpl;
import com.gargoylesoftware.css.parser.CSSOMParser;

public class CSSMediaRuleImplTest {
    @Test
    public void serializesAndDeserializesMediaRuleState() throws Exception {
        final CSSMediaRuleImpl mediaRule = parseMediaRule("@media screen { body { color: blue; } }");

        final CSSMediaRuleImpl copy = serializeAndDeserialize(mediaRule);

        assertEquals("screen", copy.getMediaList().getMediaText());
        assertEquals(mediaRule.getCssText(), copy.getCssText());
        assertEquals(1, copy.getCssRules().getLength());

        final AbstractCSSRuleImpl copiedRule = copy.getCssRules().getRules().get(0);
        assertNotNull(copiedRule);
        assertSame(copy, copiedRule.getParentRule());
    }

    private CSSMediaRuleImpl parseMediaRule(final String rule) throws IOException {
        final CSSOMParser parser = new CSSOMParser();
        return assertInstanceOf(CSSMediaRuleImpl.class, parser.parseRule(rule));
    }

    private CSSMediaRuleImpl serializeAndDeserialize(final CSSMediaRuleImpl mediaRule)
            throws IOException, ClassNotFoundException {
        final byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(mediaRule);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return assertInstanceOf(CSSMediaRuleImpl.class, input.readObject());
        }
    }
}
