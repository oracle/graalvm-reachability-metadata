/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jspapi.jsp_api;

import java.util.Collections;

import javax.servlet.jsp.tagext.BodyTag;
import javax.servlet.jsp.tagext.Tag;
import javax.servlet.jsp.tagext.TagSupport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TagSupportTest {
    @Test
    void findsAncestorWithTagInterfaceClass() {
        TagSupport grandparent = new TagSupport();
        TagSupport parent = new TagSupport();
        TagSupport child = new TagSupport();
        parent.setParent(grandparent);
        child.setParent(parent);

        Tag invalidAncestorType = TagSupport.findAncestorWithClass(child, Object.class);
        Tag nearestTagAncestor = TagSupport.findAncestorWithClass(child, Tag.class);
        Tag nearestConcreteAncestor = TagSupport.findAncestorWithClass(child, parent.getClass());
        Tag missingAncestor = TagSupport.findAncestorWithClass(child, BodyTag.class);

        assertThat(invalidAncestorType).isNull();
        assertThat(nearestTagAncestor).isSameAs(parent);
        assertThat(nearestConcreteAncestor).isSameAs(parent);
        assertThat(missingAncestor).isNull();
    }

    @Test
    void managesTagStateAndScopedValues() {
        TagSupport tag = new TagSupport();
        TagSupport parent = new TagSupport();
        tag.setParent(parent);
        tag.setId("summary");
        tag.setValue("first", "alpha");
        tag.setValue("second", "beta");

        assertThat(tag.getParent()).isSameAs(parent);
        assertThat(tag.getId()).isEqualTo("summary");
        assertThat(tag.getValue("first")).isEqualTo("alpha");
        assertThat(Collections.list(tag.getValues())).containsExactlyInAnyOrder("first", "second");

        tag.removeValue("first");
        assertThat(tag.getValue("first")).isNull();
        assertThat(Collections.list(tag.getValues())).containsExactly("second");

        tag.release();
        assertThat(tag.getParent()).isNull();
        assertThat(tag.getId()).isNull();
        assertThat(tag.getValues()).isNull();
    }
}
