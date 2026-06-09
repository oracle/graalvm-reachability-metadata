/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jspapi.jsp_api;

import javax.servlet.jsp.tagext.SimpleTagSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleTagSupportTest {

    @BeforeAll
    static void reachesJspTagClassLookupBeforeAnyOtherSimpleTagSupportUse() {
        final SimpleTagSupport parent = new SimpleTagSupport();
        final SimpleTagSupport child = new SimpleTagSupport();
        child.setParent(parent);

        assertThat(SimpleTagSupport.findAncestorWithClass(child, SimpleTagSupport.class)).isSameAs(parent);
    }

    @Test
    void findsSimpleTagAncestorByAssignableClass() {
        final SimpleTagSupport parent = new SimpleTagSupport();
        final SimpleTagSupport child = new SimpleTagSupport();
        child.setParent(parent);

        assertThat(SimpleTagSupport.findAncestorWithClass(child, SimpleTagSupport.class)).isSameAs(parent);
    }

    @Test
    void findsSimpleTagAncestorByMarkerInterface() {
        final MarkerTag parent = new MarkerTag();
        final SimpleTagSupport child = new SimpleTagSupport();
        child.setParent(parent);

        assertThat(SimpleTagSupport.findAncestorWithClass(child, Marker.class)).isSameAs(parent);
    }

    @Test
    void rejectsLookupClassOutsideJspTagHierarchy() {
        final SimpleTagSupport child = new SimpleTagSupport();

        assertThat(SimpleTagSupport.findAncestorWithClass(child, String.class)).isNull();
    }

    private interface Marker {
    }

    private static final class MarkerTag extends SimpleTagSupport implements Marker {
    }
}
