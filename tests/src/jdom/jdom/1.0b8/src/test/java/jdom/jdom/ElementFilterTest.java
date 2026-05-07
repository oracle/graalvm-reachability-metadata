/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.filter.ElementFilter;
import org.junit.jupiter.api.Test;

public class ElementFilterTest {
    @Test
    void matchesElementsByNameAndNamespace() {
        Namespace namespace = Namespace.getNamespace("book", "urn:jdom:book");
        ElementFilter filter = new ElementFilter("chapter", namespace);

        Element matchingElement = new Element("chapter", namespace);
        Element elementWithWrongNamespace = new Element("chapter");
        Element elementWithWrongName = new Element("appendix", namespace);

        assertThat(filter.canAdd(matchingElement)).isTrue();
        assertThat(filter.canRemove(elementWithWrongNamespace)).isTrue();
        assertThat(filter.matches(matchingElement)).isTrue();
        assertThat(filter.matches(elementWithWrongNamespace)).isFalse();
        assertThat(filter.matches(elementWithWrongName)).isFalse();
        assertThat(filter.matches("chapter")).isFalse();
    }
}
