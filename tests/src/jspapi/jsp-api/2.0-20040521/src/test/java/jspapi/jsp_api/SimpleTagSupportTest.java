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
    void findsAncestorOfRequestedSimpleTagClass() {
        SimpleTagSupport child = new SimpleTagSupport();
        SimpleTagSupport parent = new SimpleTagSupport();
        child.setParent(parent);

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, SimpleTagSupport.class);

        assertThat(ancestor).isSameAs(parent);
    }

    @Test
    void findsAncestorByJspTagInterfaceThroughTagAdapter() {
        SimpleTagSupport child = new SimpleTagSupport();
        SimpleTagSupport parent = new SimpleTagSupport();
        child.setParent(new TagAdapter(parent));

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, JspTag.class);

        assertThat(ancestor).isSameAs(parent);
    }

    @Test
    void returnsNullWhenRequestedClassIsNotAJspTagType() {
        SimpleTagSupport child = new SimpleTagSupport();
        child.setParent(new SimpleTagSupport());

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, Object.class);

        assertThat(ancestor).isNull();
    }
}
