/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jspapi.jsp_api;

import javax.servlet.jsp.tagext.JspTag;
import javax.servlet.jsp.tagext.SimpleTagSupport;
import javax.servlet.jsp.tagext.TagAdapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleTagSupportTest {
    @Test
    void findsNearestAncestorUsingSimpleTagParentChain() {
        SimpleTagSupport grandparent = new SimpleTagSupport();
        SimpleTagSupport parent = new SimpleTagSupport();
        SimpleTagSupport child = new SimpleTagSupport();
        parent.setParent(grandparent);
        child.setParent(parent);

        JspTag nearestJspTagAncestor = SimpleTagSupport.findAncestorWithClass(child, JspTag.class);
        JspTag nearestSimpleTagAncestor = SimpleTagSupport.findAncestorWithClass(child, parent.getClass());
        JspTag unsupportedAncestor = SimpleTagSupport.findAncestorWithClass(child, Object.class);

        assertThat(nearestJspTagAncestor).isSameAs(parent);
        assertThat(nearestSimpleTagAncestor).isSameAs(parent);
        assertThat(unsupportedAncestor).isNull();
    }

    @Test
    void unwrapsTagAdapterAncestorBeforeMatching() {
        SimpleTagSupport adaptedParent = new SimpleTagSupport();
        SimpleTagSupport child = new SimpleTagSupport();
        child.setParent(new TagAdapter(adaptedParent));

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, SimpleTagSupport.class);

        assertThat(ancestor).isSameAs(adaptedParent);
    }
}
