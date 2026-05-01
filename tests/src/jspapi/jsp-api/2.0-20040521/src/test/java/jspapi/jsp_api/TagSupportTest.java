/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jspapi.jsp_api;

import javax.servlet.jsp.tagext.Tag;
import javax.servlet.jsp.tagext.TagSupport;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TagSupportTest {
    @Test
    @Order(1)
    void primesTagInterfaceLookupThroughPublicApi() {
        TagSupport child = new TagSupport();
        TagSupport parent = new TagSupport();
        child.setParent(parent);

        Tag ancestor = TagSupport.findAncestorWithClass(child, Tag.class);

        assertThat(ancestor).isSameAs(parent);
    }

    @Test
    @Order(2)
    void findsAncestorOfRequestedTagSupportClass() {
        TagSupport child = new TagSupport();
        TagSupport parent = new TagSupport();
        child.setParent(parent);

        Tag ancestor = TagSupport.findAncestorWithClass(child, TagSupport.class);

        assertThat(ancestor).isSameAs(parent);
    }

    @Test
    @Order(3)
    void findsAncestorByTagInterface() {
        TagSupport child = new TagSupport();
        TagSupport parent = new TagSupport();
        child.setParent(parent);

        Tag ancestor = TagSupport.findAncestorWithClass(child, Tag.class);

        assertThat(ancestor).isSameAs(parent);
    }

    @Test
    @Order(4)
    void returnsNullWhenRequestedClassIsNotATagType() {
        TagSupport child = new TagSupport();
        child.setParent(new TagSupport());

        Tag ancestor = TagSupport.findAncestorWithClass(child, Object.class);

        assertThat(ancestor).isNull();
    }

}
