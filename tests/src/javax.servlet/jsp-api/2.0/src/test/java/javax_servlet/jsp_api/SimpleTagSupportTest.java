/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jsp_api;

import org.junit.jupiter.api.Test;

import javax.servlet.jsp.tagext.BodyTagSupport;
import javax.servlet.jsp.tagext.SimpleTag;
import javax.servlet.jsp.tagext.SimpleTagSupport;
import javax.servlet.jsp.tagext.TagAdapter;
import javax.servlet.jsp.tagext.TagSupport;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SimpleTagSupportTest {
    @Test
    void findsNearestMatchingSimpleTagAncestor() {
        SimpleTagSupport outer = new SimpleTagSupport();
        SimpleTagSupport inner = new SimpleTagSupport();
        SimpleTagSupport child = new SimpleTagSupport();
        inner.setParent(outer);
        child.setParent(inner);

        assertSame(inner, SimpleTagSupport.findAncestorWithClass(child, SimpleTagSupport.class));
        assertSame(inner, SimpleTagSupport.findAncestorWithClass(child, SimpleTag.class));
    }

    @Test
    void findsClassicTagAncestorBeyondAnIntermediateTag() {
        BodyTagSupport outer = new BodyTagSupport();
        TagSupport inner = new TagSupport();
        SimpleTagSupport child = new SimpleTagSupport();
        inner.setParent(outer);
        child.setParent(inner);

        assertSame(outer, SimpleTagSupport.findAncestorWithClass(child, BodyTagSupport.class));
        assertSame(inner, SimpleTagSupport.findAncestorWithClass(child, TagSupport.class));
    }

    @Test
    void findsSimpleTagAncestorThroughAClassicTagAdapter() {
        SimpleTagSupport outer = new SimpleTagSupport();
        TagSupport inner = new TagSupport();
        SimpleTagSupport child = new SimpleTagSupport();
        inner.setParent(new TagAdapter(outer));
        child.setParent(inner);

        assertSame(outer, SimpleTagSupport.findAncestorWithClass(child, SimpleTagSupport.class));
    }

    @Test
    void returnsNullWhenNoAncestorMatches() {
        SimpleTagSupport outer = new SimpleTagSupport();
        SimpleTagSupport child = new SimpleTagSupport();
        child.setParent(outer);

        assertNull(SimpleTagSupport.findAncestorWithClass(child, BodyTagSupport.class));
        assertNull(SimpleTagSupport.findAncestorWithClass(outer, SimpleTagSupport.class));
    }
}
