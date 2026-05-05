/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_xpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import net.sourceforge.htmlunit.xpath.XPath;
import net.sourceforge.htmlunit.xpath.XPathAPI;
import net.sourceforge.htmlunit.xpath.XPathContext;
import net.sourceforge.htmlunit.xpath.compiler.FunctionTable;
import net.sourceforge.htmlunit.xpath.functions.FuncTrue;
import net.sourceforge.htmlunit.xpath.objects.XObject;
import net.sourceforge.htmlunit.xpath.xml.utils.PrefixResolverDefault;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public class FunctionTableTest {
    @Test
    void builtInFunctionIsCreatedThroughFunctionTable() throws Exception {
        Document document = parse(
                """
                <root>
                    <message>HtmlUnit XPath</message>
                </root>
                """);

        XObject result = XPathAPI.eval(document, "contains(/root/message, 'XPath')");

        assertThat(result.bool()).isTrue();
    }

    @Test
    void installedFunctionIsCreatedThroughFunctionTable() throws Exception {
        FunctionTable functionTable = new FunctionTable();
        functionTable.installFunction("customTrue", FuncTrue.class);
        Document document = parse("<root/>");
        PrefixResolverDefault prefixResolver =
                new PrefixResolverDefault(document.getDocumentElement());
        XPath xpath = new XPath("customTrue()", prefixResolver, XPath.SELECT, null, functionTable);
        XPathContext xpathContext = new XPathContext(false);

        XObject result = xpath.execute(xpathContext, document.getDocumentElement(), prefixResolver);

        assertThat(result.bool()).isTrue();
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }
}
