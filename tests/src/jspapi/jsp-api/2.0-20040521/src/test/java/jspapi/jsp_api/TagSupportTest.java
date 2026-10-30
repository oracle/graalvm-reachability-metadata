/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jspapi.jsp_api;

import org.junit.jupiter.api.Test;

import javax.servlet.jsp.tagext.TagSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class TagSupportTest {
    @Test
    void findsNearestMatchingAncestorThroughIntermediateTags() {
        ContainerTag outer = new ContainerTag();
        ContainerTag inner = new ContainerTag();
        TagSupport intermediate = new TagSupport();
        TagSupport child = new TagSupport();
        inner.setParent(outer);
        intermediate.setParent(inner);
        child.setParent(intermediate);

        assertThat(TagSupport.findAncestorWithClass(child, ContainerTag.class)).isSameAs(inner);
        assertThat(TagSupport.findAncestorWithClass(inner, ContainerTag.class)).isSameAs(outer);
        assertThat(TagSupport.findAncestorWithClass(child, TagSupport.class)).isSameAs(intermediate);
    }

    @Test
    void findsAncestorImplementingAnApplicationInterface() {
        ContainerTag container = new ContainerTag();
        container.setId("catalog");
        container.setValue("category", "books");
        TagSupport intermediate = new TagSupport();
        intermediate.setParent(container);
        TagSupport child = new TagSupport();
        child.setParent(intermediate);

        assertThat(TagSupport.findAncestorWithClass(child, ContainerScope.class)).isSameAs(container);
        ContainerScope scope = (ContainerScope) TagSupport.findAncestorWithClass(child, ContainerScope.class);
        assertThat(scope.getName()).isEqualTo("catalog");
        assertThat(((TagSupport) scope).getValue("category")).isEqualTo("books");
    }

    @Test
    void returnsNullWhenThereIsNoMatchingAncestor() {
        ContainerTag child = new ContainerTag();
        TagSupport parent = new TagSupport();
        child.setParent(parent);

        assertThat(TagSupport.findAncestorWithClass(child, ContainerTag.class)).isNull();
        assertThat(TagSupport.findAncestorWithClass(child, ContainerScope.class)).isNull();
        assertThat(TagSupport.findAncestorWithClass(parent, TagSupport.class)).isNull();
        assertThat(TagSupport.findAncestorWithClass(child, String.class)).isNull();
        assertThat(TagSupport.findAncestorWithClass(child, null)).isNull();
        assertThat(TagSupport.findAncestorWithClass(null, TagSupport.class)).isNull();
    }

    public interface ContainerScope {
        String getName();
    }

    public static class ContainerTag extends TagSupport implements ContainerScope {
        @Override
        public String getName() {
            return getId();
        }
    }
}
