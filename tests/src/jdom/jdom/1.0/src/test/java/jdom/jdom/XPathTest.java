/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.xpath.XPath;
import org.junit.jupiter.api.Test;

public class XPathTest {
    private static final String XPATH_CLASS_PROPERTY = "org.jdom.xpath.class";

    @Test
    void newInstanceCreatesDefaultImplementationAndEvaluatesNamespacedExpression() throws Exception {
        String previousXPathClass = System.getProperty(XPATH_CLASS_PROPERTY);
        System.setProperty(XPATH_CLASS_PROPERTY, "org.jdom.xpath.JaxenXPath");

        try {
            Namespace bookNamespace = Namespace.getNamespace("bk", "urn:books");
            Element title = new Element("title", bookNamespace).setText("JDOM in Action");
            Element catalog = new Element("catalog", bookNamespace)
                    .addContent(new Element("book", bookNamespace).addContent(title));

            XPath xPath = XPath.newInstance("bk:book/bk:title");
            xPath.addNamespace(bookNamespace);

            List selectedNodes = xPath.selectNodes(catalog);

            assertThat(xPath.getXPath()).contains("bk:book").contains("bk:title");
            assertThat(selectedNodes).containsExactly(title);
            assertThat(xPath.selectSingleNode(catalog)).isSameAs(title);
            assertThat(xPath.valueOf(catalog)).isEqualTo("JDOM in Action");
        } finally {
            if (previousXPathClass == null) {
                System.clearProperty(XPATH_CLASS_PROPERTY);
            } else {
                System.setProperty(XPATH_CLASS_PROPERTY, previousXPathClass);
            }
        }
    }
}
