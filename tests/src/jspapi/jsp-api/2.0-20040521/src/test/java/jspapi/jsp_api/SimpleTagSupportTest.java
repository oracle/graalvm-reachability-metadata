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

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SimpleTagSupportTest {
    @Test
    @Order(1)
    void findsAncestorWithRequestedJspTagType() {
        SimpleTagSupport parent = new SimpleTagSupport();
        SimpleTagSupport child = new SimpleTagSupport();
        child.setParent(parent);

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, JspTag.class);

        assertThat(ancestor).isSameAs(parent);
    }

    @Test
    @Order(2)
    void findsAncestorWithRequestedConcreteAndInterfaceTypes() {
        MarkerSimpleTagSupport grandparent = new MarkerSimpleTagSupport();
        SimpleTagSupport parent = new SimpleTagSupport();
        SimpleTagSupport child = new SimpleTagSupport();
        parent.setParent(grandparent);
        child.setParent(parent);

        assertThat(SimpleTagSupport.findAncestorWithClass(child, SimpleTagSupport.class)).isSameAs(parent);
        assertThat(SimpleTagSupport.findAncestorWithClass(child, Marker.class)).isSameAs(grandparent);
    }

    private interface Marker {
    }

    private static final class MarkerSimpleTagSupport extends SimpleTagSupport implements Marker {
    }
}
