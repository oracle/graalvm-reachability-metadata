/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jsp_api;

import java.util.Collections;
import java.util.List;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.IterationTag;
import javax.servlet.jsp.tagext.Tag;
import javax.servlet.jsp.tagext.TagSupport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TagSupportTest {
    @Test
    void findAncestorWithClassReturnsNearestAssignableParent() {
        TagSupport grandparent = new MarkerTag();
        TagSupport intermediateParent = new PlainTag();
        TagSupport parent = new TagSupport();
        TagSupport child = new TagSupport();
        intermediateParent.setParent(grandparent);
        parent.setParent(intermediateParent);
        child.setParent(parent);
        Class<? extends TagSupport> parentType = parent.getClass();

        assertThat(TagSupport.findAncestorWithClass(child, Tag.class)).isSameAs(parent);
        assertThat(TagSupport.findAncestorWithClass(child, parentType)).isSameAs(parent);
        assertThat(TagSupport.findAncestorWithClass(parent, Marker.class)).isSameAs(grandparent);
        assertThat(TagSupport.findAncestorWithClass(parent, PlainTag.class)).isSameAs(intermediateParent);
        assertThat(TagSupport.findAncestorWithClass(child, IterationTag.class)).isSameAs(parent);
    }

    @Test
    void findAncestorWithClassRejectsInvalidInputsAndMissingAncestors() {
        TagSupport child = new TagSupport();

        assertThat(TagSupport.findAncestorWithClass(null, TagSupport.class)).isNull();
        assertThat(TagSupport.findAncestorWithClass(child, null)).isNull();
        assertThat(TagSupport.findAncestorWithClass(child, Object.class)).isNull();
        assertThat(TagSupport.findAncestorWithClass(child, TagSupport.class)).isNull();
    }

    @Test
    void lifecycleMethodsUseDefaultTagContract() throws JspException {
        TagSupport tag = new TagSupport();

        assertThat(tag.doStartTag()).isEqualTo(Tag.SKIP_BODY);
        assertThat(tag.doEndTag()).isEqualTo(Tag.EVAL_PAGE);
        assertThat(tag.doAfterBody()).isEqualTo(Tag.SKIP_BODY);
    }

    @Test
    void releaseClearsManagedState() {
        TagSupport tag = new TagSupport();
        tag.setId("tag-id");
        tag.setParent(new TagSupport());
        tag.setValue("name", "value");

        tag.release();

        assertThat(tag.getId()).isNull();
        assertThat(tag.getParent()).isNull();
        assertThat(tag.getValue("name")).isNull();
        assertThat(tag.getValues()).isNull();
    }

    @Test
    void storesAndRemovesNamedValues() {
        TagSupport tag = new TagSupport();
        tag.setValue("first", "one");
        tag.setValue("second", "two");

        List<String> keys = Collections.list(tag.getValues());

        assertThat(tag.getValue("first")).isEqualTo("one");
        assertThat(keys).containsExactlyInAnyOrder("first", "second");

        tag.removeValue("first");

        assertThat(tag.getValue("first")).isNull();
        assertThat(tag.getValue("second")).isEqualTo("two");
    }

    private interface Marker {
    }

    private static final class MarkerTag extends TagSupport implements Marker {
    }

    private static final class PlainTag extends TagSupport {
    }
}
