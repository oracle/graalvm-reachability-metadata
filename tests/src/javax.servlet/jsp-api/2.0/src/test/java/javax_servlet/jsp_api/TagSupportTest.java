/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jsp_api;

import org.junit.jupiter.api.Test;

import javax.servlet.jsp.tagext.BodyTag;
import javax.servlet.jsp.tagext.BodyTagSupport;
import javax.servlet.jsp.tagext.IterationTag;
import javax.servlet.jsp.tagext.Tag;
import javax.servlet.jsp.tagext.TagSupport;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TagSupportTest {
    @Test
    void findsNearestMatchingAncestorWithoutMatchingTheChild() {
        TagSupport outer = new TagSupport();
        TagSupport inner = new TagSupport();
        TagSupport child = new TagSupport();
        inner.setParent(outer);
        child.setParent(inner);

        assertSame(inner, TagSupport.findAncestorWithClass(child, TagSupport.class));
        assertSame(inner, TagSupport.findAncestorWithClass(child, Tag.class));
        assertSame(outer, TagSupport.findAncestorWithClass(inner, TagSupport.class));
    }

    @Test
    void walksPastNonmatchingParentsToFindABodyTag() {
        BodyTagSupport outer = new BodyTagSupport();
        TagSupport inner = new TagSupport();
        TagSupport child = new TagSupport();
        inner.setParent(outer);
        child.setParent(inner);

        assertSame(outer, TagSupport.findAncestorWithClass(child, BodyTagSupport.class));
        assertSame(outer, TagSupport.findAncestorWithClass(child, BodyTag.class));
        assertSame(inner, TagSupport.findAncestorWithClass(child, IterationTag.class));
    }

    @Test
    void findsAncestorImplementingAnApplicationInterface() {
        ContainerTag outer = new ContainerTag();
        TagSupport inner = new TagSupport();
        TagSupport child = new TagSupport();
        inner.setParent(outer);
        child.setParent(inner);

        assertSame(outer, TagSupport.findAncestorWithClass(child, Container.class));
    }

    @Test
    void returnsNullWhenThereIsNoMatchingAncestor() {
        TagSupport parent = new TagSupport();
        TagSupport child = new TagSupport();
        child.setParent(parent);

        assertNull(TagSupport.findAncestorWithClass(child, BodyTagSupport.class));
        assertNull(TagSupport.findAncestorWithClass(child, Container.class));
        assertNull(TagSupport.findAncestorWithClass(parent, TagSupport.class));
        assertNull(TagSupport.findAncestorWithClass(child, String.class));
        assertNull(TagSupport.findAncestorWithClass(child, null));
        assertNull(TagSupport.findAncestorWithClass(null, TagSupport.class));
    }

    public interface Container {
    }

    public static class ContainerTag extends TagSupport implements Container {
    }
}
