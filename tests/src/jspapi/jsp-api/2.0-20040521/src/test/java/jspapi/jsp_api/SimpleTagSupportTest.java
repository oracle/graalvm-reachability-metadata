/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jspapi.jsp_api;

import org.junit.jupiter.api.Test;

import javax.servlet.jsp.tagext.SimpleTagSupport;
import javax.servlet.jsp.tagext.TagAdapter;
import javax.servlet.jsp.tagext.TagSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleTagSupportTest {
    @Test
    void findsNearestMatchingAncestor() {
        ContainerTag outer = new ContainerTag();
        ContainerTag inner = new ContainerTag();
        SimpleTagSupport intermediate = new SimpleTagSupport();
        SimpleTagSupport child = new SimpleTagSupport();
        inner.setParent(outer);
        intermediate.setParent(inner);
        child.setParent(intermediate);

        assertThat(SimpleTagSupport.findAncestorWithClass(child, ContainerTag.class)).isSameAs(inner);
        assertThat(SimpleTagSupport.findAncestorWithClass(inner, ContainerTag.class)).isSameAs(outer);
        assertThat(SimpleTagSupport.findAncestorWithClass(child, SimpleTagSupport.class)).isSameAs(intermediate);
    }

    @Test
    void traversesClassicTagsAndUnwrapsAdaptedSimpleTags() {
        ContainerTag container = new ContainerTag();
        SimpleTagSupport intermediate = new SimpleTagSupport();
        intermediate.setParent(container);
        TagSupport classic = new TagSupport();
        classic.setParent(new TagAdapter(intermediate));
        SimpleTagSupport child = new SimpleTagSupport();
        child.setParent(classic);

        assertThat(SimpleTagSupport.findAncestorWithClass(child, TagSupport.class)).isSameAs(classic);
        assertThat(SimpleTagSupport.findAncestorWithClass(classic, SimpleTagSupport.class)).isSameAs(intermediate);
        assertThat(SimpleTagSupport.findAncestorWithClass(child, ContainerTag.class)).isSameAs(container);
    }

    @Test
    void returnsNullWhenNoAncestorMatches() {
        ContainerTag child = new ContainerTag();
        SimpleTagSupport parent = new SimpleTagSupport();
        child.setParent(parent);

        assertThat(SimpleTagSupport.findAncestorWithClass(child, ContainerTag.class)).isNull();
        assertThat(SimpleTagSupport.findAncestorWithClass(parent, SimpleTagSupport.class)).isNull();
    }

    public static class ContainerTag extends SimpleTagSupport {
    }
}
