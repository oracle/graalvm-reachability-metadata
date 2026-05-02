/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jsp_api;

import javax.servlet.jsp.tagext.JspTag;
import javax.servlet.jsp.tagext.SimpleTagSupport;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(OrderAnnotation.class)
public class SimpleTagSupportTest {

    @Test
    @Order(1)
    void initializesJspTagContractLookupBeforeFindingAncestor() {
        SimpleTagSupport ancestor = new SimpleTagSupport();
        SimpleTagSupport child = new SimpleTagSupport();
        child.setParent(ancestor);

        JspTag found = SimpleTagSupport.findAncestorWithClass(child, JspTag.class);

        assertThat(found).isSameAs(ancestor);
    }

    @Test
    @Order(2)
    void findsAncestorByConcreteSimpleTagSupportClassAfterLookupIsCached() {
        SimpleTagSupport ancestor = new SimpleTagSupport();
        SimpleTagSupport child = new SimpleTagSupport();
        child.setParent(ancestor);

        JspTag found = SimpleTagSupport.findAncestorWithClass(child, SimpleTagSupport.class);

        assertThat(found).isSameAs(ancestor);
    }
}
