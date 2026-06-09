/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jspapi.jsp_api;

import java.util.Collections;

import javax.servlet.jsp.tagext.TagSupport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TagSupportTest {

    @Test
    void findsNearestAncestorByConcreteTagClass() {
        final TagSupport grandparent = new TagSupport();
        final TagSupport parent = new TagSupport();
        final TagSupport child = new TagSupport();
        parent.setParent(grandparent);
        child.setParent(parent);

        assertThat(TagSupport.findAncestorWithClass(child, TagSupport.class)).isSameAs(parent);
    }

    @Test
    void findsAncestorByMarkerInterface() {
        final MarkerTag parent = new MarkerTag();
        final TagSupport child = new TagSupport();
        child.setParent(parent);

        assertThat(TagSupport.findAncestorWithClass(child, Marker.class)).isSameAs(parent);
    }

    @Test
    void rejectsLookupClassOutsideTagHierarchy() {
        final TagSupport child = new TagSupport();

        assertThat(TagSupport.findAncestorWithClass(child, String.class)).isNull();
    }

    @Test
    void storesAndReleasesTagState() {
        final TagSupport parent = new TagSupport();
        final TagSupport child = new TagSupport();
        child.setParent(parent);
        child.setId("example");
        child.setValue("name", "value");

        assertThat(child.getParent()).isSameAs(parent);
        assertThat(child.getId()).isEqualTo("example");
        assertThat(child.getValue("name")).isEqualTo("value");
        assertThat(Collections.list(child.getValues())).containsExactly("name");

        child.release();

        assertThat(child.getParent()).isNull();
        assertThat(child.getId()).isNull();
        assertThat(child.getValue("name")).isNull();
        assertThat(child.getValues()).isNull();
    }

    private interface Marker {
    }

    private static final class MarkerTag extends TagSupport implements Marker {
    }
}
