/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.servlet_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.Tag;
import javax.servlet.jsp.tagext.TagSupport;

import org.junit.jupiter.api.Test;

public class TagSupportTest {
    @Test
    void findAncestorWithClassReturnsNearestAssignableParent() {
        final TagSupport grandparent = new MarkerTag();
        final TagSupport intermediateParent = new PlainTag();
        final TagSupport parent = new TagSupport();
        final TagSupport child = new TagSupport();
        intermediateParent.setParent(grandparent);
        parent.setParent(intermediateParent);
        child.setParent(parent);

        assertThat(TagSupport.findAncestorWithClass(child, Tag.class)).isSameAs(parent);
        assertThat(TagSupport.findAncestorWithClass(child, TagSupport.class)).isSameAs(parent);
        assertThat(TagSupport.findAncestorWithClass(parent, TagSupportTestMarker.class)).isSameAs(grandparent);
        assertThat(TagSupport.findAncestorWithClass(parent, PlainTag.class)).isSameAs(intermediateParent);
    }

    @Test
    void findAncestorWithClassRejectsInvalidInputsAndMissingAncestors() {
        final TagSupport child = new TagSupport();

        assertThat(TagSupport.findAncestorWithClass(null, TagSupport.class)).isNull();
        assertThat(TagSupport.findAncestorWithClass(child, null)).isNull();
        assertThat(TagSupport.findAncestorWithClass(child, String.class)).isNull();
        assertThat(TagSupport.findAncestorWithClass(child, TagSupport.class)).isNull();
    }

    @Test
    void lifecycleMethodsUseDefaultTagContract() throws JspException {
        final TagSupport tag = new TagSupport();

        assertThat(tag.doStartTag()).isEqualTo(Tag.SKIP_BODY);
        assertThat(tag.doEndTag()).isEqualTo(Tag.EVAL_PAGE);
        assertThat(tag.doAfterBody()).isEqualTo(Tag.SKIP_BODY);
    }

    @Test
    void stateAccessorsManageIdParentAndValues() {
        final TagSupport tag = new TagSupport();
        final TagSupport parent = new TagSupport();

        assertThat(tag.getParent()).isNull();
        assertThat(tag.getId()).isNull();
        assertThat(tag.getValue("missing")).isNull();
        assertThat(tag.getValues()).isNull();

        tag.setParent(parent);
        tag.setPageContext(null);
        tag.setId("tagId");
        tag.setValue("number", Integer.valueOf(42));
        tag.setValue("text", "value");

        assertThat(tag.getParent()).isSameAs(parent);
        assertThat(tag.getId()).isEqualTo("tagId");
        assertThat(tag.getValue("number")).isEqualTo(Integer.valueOf(42));
        assertThat(tag.getValue("text")).isEqualTo("value");
        assertThat(Collections.list(tag.getValues())).containsExactlyInAnyOrder("number", "text");

        tag.removeValue("number");
        assertThat(tag.getValue("number")).isNull();
        assertThat(Collections.list(tag.getValues())).containsExactly("text");

        tag.release();
        assertThat(tag.getParent()).isNull();
        assertThat(tag.getId()).isEqualTo("tagId");
        assertThat(tag.getValue("text")).isEqualTo("value");
    }

    private interface TagSupportTestMarker {
    }

    private static final class MarkerTag extends TagSupport implements TagSupportTestMarker {
    }

    private static final class PlainTag extends TagSupport {
    }
}
