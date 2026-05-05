/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jspapi.jsp_api;

import javax.servlet.jsp.tagext.BodyTag;
import javax.servlet.jsp.tagext.JspTag;
import javax.servlet.jsp.tagext.SimpleTagSupport;
import javax.servlet.jsp.tagext.TagAdapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleTagSupportTest {
    @Test
    void findAncestorWithClassFindsClosestSimpleTagParent() {
        SimpleTagSupport grandparent = new SimpleTagSupport();
        SimpleTagSupport parent = new SimpleTagSupport();
        SimpleTagSupport child = new SimpleTagSupport();

        parent.setParent(grandparent);
        child.setParent(parent);

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, SimpleTagSupport.class);

        assertThat(ancestor).isSameAs(parent);
    }

    @Test
    void findAncestorWithClassFindsAncestorByNonTagInterface() {
        InterfaceParentTag parent = new InterfaceParentTag();
        SimpleTagSupport child = new SimpleTagSupport();

        child.setParent(parent);

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, Marker.class);

        assertThat(ancestor).isSameAs(parent);
    }

    @Test
    void findAncestorWithClassReturnsNullWhenNoAncestorMatches() {
        SimpleTagSupport parent = new SimpleTagSupport();
        SimpleTagSupport child = new SimpleTagSupport();

        child.setParent(parent);

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, BodyTag.class);

        assertThat(ancestor).isNull();
    }

    @Test
    void findAncestorWithClassRejectsClassesThatAreNotTagsOrInterfaces() {
        SimpleTagSupport child = new SimpleTagSupport();

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, String.class);

        assertThat(ancestor).isNull();
    }

    @Test
    void findAncestorWithClassComparesAdapteeForTagAdapterParents() {
        SimpleTagSupport adaptedParent = new SimpleTagSupport();
        TagAdapter parentAdapter = new TagAdapter(adaptedParent);
        SimpleTagSupport child = new SimpleTagSupport();

        child.setParent(parentAdapter);

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, SimpleTagSupport.class);

        assertThat(ancestor).isSameAs(adaptedParent);
    }

    private interface Marker {
    }

    private static final class InterfaceParentTag extends SimpleTagSupport implements Marker {
    }
}
