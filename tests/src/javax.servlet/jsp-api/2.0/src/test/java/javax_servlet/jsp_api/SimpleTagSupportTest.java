/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jsp_api;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.JspTag;
import javax.servlet.jsp.tagext.SimpleTagSupport;
import javax.servlet.jsp.tagext.Tag;
import javax.servlet.jsp.tagext.TagAdapter;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(OrderAnnotation.class)
public class SimpleTagSupportTest {
    @Test
    @Order(1)
    void findAncestorWithClassAcceptsJspTagTypeThroughPublicApi() {
        SimpleTagSupport child = new SimpleTagSupport();

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, JspTag.class);

        assertThat(ancestor).isNull();
    }

    @Test
    @Order(2)
    void findAncestorWithClassFindsNearestMatchingSimpleTagAncestor() {
        SimpleTagSupport child = new SimpleTagSupport();
        SpecializedSimpleTag parent = new SpecializedSimpleTag();
        SpecializedSimpleTag grandparent = new SpecializedSimpleTag();
        child.setParent(parent);
        parent.setParent(grandparent);

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, SpecializedSimpleTag.class);

        assertThat(ancestor).isSameAs(parent);
    }

    @Test
    @Order(3)
    void findAncestorWithClassComparesTagAdapterAdaptee() {
        SimpleTagSupport child = new SimpleTagSupport();
        MarkedSimpleTag adaptee = new MarkedSimpleTag();
        child.setParent(new TagAdapter(adaptee));

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, MarkerTag.class);

        assertThat(ancestor).isSameAs(adaptee);
    }

    @Test
    @Order(4)
    void findAncestorWithClassTraversesClassicTagParents() {
        ClassicTag child = new ClassicTag();
        ClassicTag intermediateParent = new ClassicTag();
        SpecializedClassicTag matchingGrandparent = new SpecializedClassicTag();
        child.setParent(intermediateParent);
        intermediateParent.setParent(matchingGrandparent);

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, SpecializedClassicTag.class);

        assertThat(ancestor).isSameAs(matchingGrandparent);
    }

    @Test
    @Order(5)
    void findAncestorWithClassRejectsNonTagClasses() {
        SimpleTagSupport child = new SimpleTagSupport();
        child.setParent(new SpecializedSimpleTag());

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, Object.class);

        assertThat(ancestor).isNull();
    }

    private interface MarkerTag {
    }

    private static final class MarkedSimpleTag extends SimpleTagSupport implements MarkerTag {
    }

    private static class ClassicTag implements Tag {
        private Tag parent;

        @Override
        public void setPageContext(PageContext pageContext) {
        }

        @Override
        public void setParent(Tag parent) {
            this.parent = parent;
        }

        @Override
        public Tag getParent() {
            return parent;
        }

        @Override
        public int doStartTag() throws JspException {
            return SKIP_BODY;
        }

        @Override
        public int doEndTag() throws JspException {
            return EVAL_PAGE;
        }

        @Override
        public void release() {
            parent = null;
        }
    }

    private static final class SpecializedClassicTag extends ClassicTag {
    }

    private static final class SpecializedSimpleTag extends SimpleTagSupport {
    }
}
