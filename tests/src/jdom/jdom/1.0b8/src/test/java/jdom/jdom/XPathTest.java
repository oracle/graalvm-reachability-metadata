/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jaxen.jdom.JDOMXPath;
import org.jdom.Element;
import org.jdom.Namespace;
import org.junit.jupiter.api.Test;

public class XPathTest {
    @Test
    void jaxenEvaluatesNamespacedExpressionAgainstJdomElements() throws Exception {
        Namespace bookNamespace = Namespace.getNamespace("bk", "urn:books");
        Element title = new Element("title", bookNamespace).setText("JDOM in Action");
        Element catalog = new Element("catalog", bookNamespace)
                .addContent(new Element("book", bookNamespace).addContent(title));

        JDOMXPath xPath = new JDOMXPath("bk:book/bk:title");
        xPath.addNamespace("bk", "urn:books");

        List selectedNodes = xPath.selectNodes(catalog);

        assertThat(selectedNodes).containsExactly(title);
        assertThat(xPath.selectSingleNode(catalog)).isSameAs(title);
        assertThat(xPath.stringValueOf(catalog)).isEqualTo("JDOM in Action");
    }
}
