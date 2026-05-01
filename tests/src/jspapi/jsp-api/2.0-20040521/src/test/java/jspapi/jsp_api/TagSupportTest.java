/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jspapi.jsp_api;

import static org.assertj.core.api.Assertions.assertThat;

import javax.servlet.jsp.tagext.IterationTag;
import javax.servlet.jsp.tagext.Tag;
import javax.servlet.jsp.tagext.TagSupport;

import org.junit.jupiter.api.Test;

public class TagSupportTest {
    private static volatile Class<?> requestedAncestorType = Object.class;

    @Test
    public void findAncestorWithClassRejectsRuntimeSelectedNonTagClass() {
        TagSupport child = new TagSupport();

        Tag ancestor = TagSupport.findAncestorWithClass(child, requestedAncestorType);

        assertThat(ancestor).isNull();
    }

    @Test
    public void findAncestorWithClassFindsParentTagByRuntimeSelectedTagInterface() {
        TagSupport parent = new TagSupport();
        TagSupport child = new TagSupport();
        child.setParent(parent);
        requestedAncestorType = IterationTag.class;

        Tag ancestor = TagSupport.findAncestorWithClass(child, requestedAncestorType);

        assertThat(ancestor).isSameAs(parent);
    }
}
