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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SimpleTagSupportTest {
    @BeforeAll
    public static void exerciseLegacyJspTagClassLookup() {
        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(new SimpleTagSupport(), Object.class);

        assertThat(ancestor).isNull();
    }

    @Test
    public void testFindAncestorWithClassChecksJspTagTypeBeforeWalkingParents() {
        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(new SimpleTagSupport(), Object.class);

        assertThat(ancestor).isNull();
    }

    @Test
    public void findAncestorWithClassFindsSimpleTagParentByInterface() {
        SimpleTagSupport parent = new SimpleTagSupport();
        SimpleTagSupport child = new SimpleTagSupport();
        child.setParent(parent);

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, JspTag.class);

        assertThat(ancestor).isSameAs(parent);
    }
}
