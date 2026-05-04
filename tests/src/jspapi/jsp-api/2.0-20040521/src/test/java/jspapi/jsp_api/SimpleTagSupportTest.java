/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jspapi.jsp_api;

import static org.assertj.core.api.Assertions.assertThat;

import javax.servlet.jsp.tagext.JspTag;
import javax.servlet.jsp.tagext.SimpleTagSupport;
import javax.servlet.jsp.tagext.TagAdapter;

import org.junit.jupiter.api.Test;

public class SimpleTagSupportTest {
    @Test
    void findsNearestSimpleTagAncestorByConcreteClass() {
        final CustomSimpleTag grandparent = new CustomSimpleTag();
        final CustomSimpleTag parent = new CustomSimpleTag();
        final SimpleTagSupport child = new SimpleTagSupport();
        parent.setParent(grandparent);
        child.setParent(parent);

        final JspTag foundByJspTagContract = SimpleTagSupport.findAncestorWithClass(child, JspTag.class);
        final JspTag foundByConcreteClass = SimpleTagSupport.findAncestorWithClass(child, CustomSimpleTag.class);

        assertThat(foundByJspTagContract).isSameAs(parent);
        assertThat(foundByConcreteClass).isSameAs(parent);
    }

    @Test
    void unwrapsTagAdapterBeforeMatchingAncestor() {
        final CustomSimpleTag adaptedParent = new CustomSimpleTag();
        final SimpleTagSupport child = new SimpleTagSupport();
        child.setParent(new TagAdapter(adaptedParent));

        final JspTag found = SimpleTagSupport.findAncestorWithClass(child, CustomSimpleTag.class);

        assertThat(found).isSameAs(adaptedParent);
    }

    @Test
    void returnsNullWhenInputsCannotIdentifyAnAncestor() {
        final SimpleTagSupport child = new SimpleTagSupport();

        assertThat(SimpleTagSupport.findAncestorWithClass(null, CustomSimpleTag.class)).isNull();
        assertThat(SimpleTagSupport.findAncestorWithClass(child, null)).isNull();
        assertThat(SimpleTagSupport.findAncestorWithClass(child, String.class)).isNull();
        assertThat(SimpleTagSupport.findAncestorWithClass(child, CustomSimpleTag.class)).isNull();
    }

    private static final class CustomSimpleTag extends SimpleTagSupport {
    }
}
