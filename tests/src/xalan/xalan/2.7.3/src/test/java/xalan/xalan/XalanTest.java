/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xalan.xalan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.apache.xalan.lib.ExsltMath;
import org.apache.xalan.lib.ExsltSets;
import org.apache.xalan.lib.ExsltStrings;
import org.apache.xalan.processor.TransformerFactoryImpl;
import org.apache.xpath.CachedXPathAPI;
import org.apache.xpath.NodeSet;
import org.apache.xpath.XPathAPI;
import org.apache.xpath.domapi.XPathEvaluatorImpl;
import org.apache.xpath.jaxp.XPathFactoryImpl;
import org.apache.xpath.objects.XObject;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.xpath.XPathExpression;
import org.w3c.dom.xpath.XPathNSResolver;
import org.w3c.dom.xpath.XPathResult;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

public class XalanTest {
    private static final String CATALOG_XML = """
            <?xml version="1.0"?>
            <catalog>
                <book id="b1" category="fiction" author="Octavia Butler">
                    <title>Kindred</title>
                    <price>8.99</price>
                </book>
                <book id="b2" category="fantasy" author="Ursula Le Guin">
                    <title>Earthsea</title>
                    <price>12.50</price>
                </book>
                <book id="b3" category="fiction" author="Octavia Butler">
                    <title>Parable</title>
                    <price>15.00</price>
                </book>
            </catalog>
            """;

    private static final String AUTHORS_XML = """
            <?xml version="1.0"?>
            <authors>
                <author name="Octavia Butler" country="US"/>
                <author name="Ursula Le Guin" country="US"/>
            </authors>
            """;

    private static final String NAMESPACED_CATALOG_XML = """
            <?xml version="1.0"?>
            <lib:catalog xmlns:lib="urn:library" xmlns:meta="urn:metadata">
                <lib:book meta:id="b1" meta:category="fiction">
                    <lib:title>Kindred</lib:title>
                </lib:book>
                <lib:book meta:id="b2" meta:category="fantasy">
                    <lib:title>Earthsea</lib:title>
                </lib:book>
                <lib:book meta:id="b3" meta:category="fiction">
                    <lib:title>Parable</lib:title>
                </lib:book>
            </lib:catalog>
            """;

    private static final String SUMMARY_STYLESHEET = """
            <?xml version="1.0"?>
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:output method="xml" omit-xml-declaration="yes" indent="no"/>
                <xsl:param name="minimumPrice" select="0"/>
                <xsl:key name="books-by-author" match="book" use="@author"/>
                <xsl:variable name="authors" select="document('authors.xml')/authors/author"/>

                <xsl:template match="/catalog">
                    <summary count="{count(book[number(price) &gt;= $minimumPrice])}">
                        <xsl:for-each select="book[number(price) &gt;= $minimumPrice]">
                            <xsl:sort select="number(price)" data-type="number" order="descending"/>
                            <xsl:variable name="authorName" select="@author"/>
                            <book id="{@id}" category="{@category}" author="{@author}"
                                  peer-count="{count(key('books-by-author', @author))}">
                                <title><xsl:value-of select="normalize-space(title)"/></title>
                                <author-country>
                                    <xsl:value-of select="$authors[@name = $authorName]/@country"/>
                                </author-country>
                                <formatted-price><xsl:value-of select="format-number(price, '0.00')"/></formatted-price>
                            </book>
                        </xsl:for-each>
                    </summary>
                </xsl:template>
            </xsl:stylesheet>
            """;

    private static final String TITLES_STYLESHEET = """
            <?xml version="1.0"?>
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:output method="xml" omit-xml-declaration="yes" indent="no"/>
                <xsl:template match="/catalog">
                    <titles>
                        <xsl:apply-templates select="book">
                            <xsl:sort select="title"/>
                        </xsl:apply-templates>
                    </titles>
                </xsl:template>
                <xsl:template match="book">
                    <title category="{@category}"><xsl:value-of select="normalize-space(title)"/></title>
                </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    public void compiledTemplatesApplyParametersKeysAndUriResolvedDocuments() throws Exception {
        TransformerFactoryImpl factory = newTransformerFactory();
        InMemoryUriResolver uriResolver = new InMemoryUriResolver(Map.of("authors.xml", AUTHORS_XML));
        factory.setURIResolver(uriResolver);
        Templates templates = factory.newTemplates(streamSource(SUMMARY_STYLESHEET, "memory:summary.xsl"));

        Transformer transformer = templates.newTransformer();
        transformer.setURIResolver(uriResolver);
        transformer.setParameter("minimumPrice", "10");
        String output = transformToString(transformer, streamSource(CATALOG_XML, "memory:catalog.xml"));

        Document result = parseXml(output);
        Element summary = result.getDocumentElement();
        assertThat(summary.getNodeName()).isEqualTo("summary");
        assertThat(summary.getAttribute("count")).isEqualTo("2");

        NodeList books = summary.getElementsByTagName("book");
        assertThat(books.getLength()).isEqualTo(2);
        Element first = (Element) books.item(0);
        assertThat(first.getAttribute("id")).isEqualTo("b3");
        assertThat(first.getAttribute("author")).isEqualTo("Octavia Butler");
        assertThat(first.getAttribute("peer-count")).isEqualTo("2");
        assertThat(textOf(first, "title")).isEqualTo("Parable");
        assertThat(textOf(first, "author-country")).isEqualTo("US");
        assertThat(textOf(first, "formatted-price")).isEqualTo("15.00");

        Element second = (Element) books.item(1);
        assertThat(second.getAttribute("id")).isEqualTo("b2");
        assertThat(second.getAttribute("peer-count")).isEqualTo("1");
        assertThat(textOf(second, "title")).isEqualTo("Earthsea");
    }

    @Test
    public void saxTransformerHandlerTransformsParserEvents() throws Exception {
        TransformerFactoryImpl factory = newTransformerFactory();
        Templates templates = factory.newTemplates(streamSource(TITLES_STYLESHEET, "memory:titles.xsl"));
        TransformerHandler handler = factory.newTransformerHandler(templates);
        StringWriter writer = new StringWriter();
        handler.setResult(new StreamResult(writer));

        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        XMLReader reader = parserFactory.newSAXParser().getXMLReader();
        reader.setContentHandler(handler);
        reader.parse(inputSource(CATALOG_XML, "memory:catalog.xml"));

        Document result = parseXml(writer.toString());
        NodeList titleElements = result.getDocumentElement().getElementsByTagName("title");
        assertThat(texts(titleElements)).containsExactly("Earthsea", "Kindred", "Parable");
        assertThat(((Element) titleElements.item(0)).getAttribute("category")).isEqualTo("fantasy");
    }

    @Test
    public void identityTransformerCopiesDomAndHonorsOutputProperties() throws Exception {
        TransformerFactoryImpl factory = newTransformerFactory();
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        Document source = parseXml(CATALOG_XML);
        Document copied = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        DOMResult domResult = new DOMResult(copied);
        transformer.transform(new DOMSource(source), domResult);
        assertThat(copied.getDocumentElement().getNodeName()).isEqualTo("catalog");
        assertThat(copied.getElementsByTagName("book").getLength()).isEqualTo(3);
        assertThat(textOf((Element) copied.getElementsByTagName("book").item(1), "title")).isEqualTo("Earthsea");

        String serialized = transformToString(transformer, new DOMSource(copied));
        assertThat(serialized).doesNotStartWith("<?xml");
        assertThat(serialized).contains("<catalog>");
    }

    @Test
    public void jaxpXPathFactoryEvaluatesVariablesAndNodeSets() throws Exception {
        XPathFactoryImpl factory = new XPathFactoryImpl();
        assertThat(factory.isObjectModelSupported(XPathFactory.DEFAULT_OBJECT_MODEL_URI)).isTrue();

        XPath xpath = factory.newXPath();
        xpath.setXPathVariableResolver(this::resolveXPathVariable);
        Document document = parseXml(CATALOG_XML);

        Double total = (Double) xpath.evaluate(
                "sum(/catalog/book[@category = $category]/price)", document, XPathConstants.NUMBER);
        assertThat(total).isCloseTo(23.99, within(0.0001));

        NodeList expensiveTitles = (NodeList) xpath.evaluate(
                "/catalog/book[number(price) >= $minimumPrice]/title", document, XPathConstants.NODESET);
        assertThat(texts(expensiveTitles)).containsExactly("Earthsea", "Parable");

        String selectedAuthor = xpath.evaluate("string(/catalog/book[@id = 'b3']/@author)", document);
        assertThat(selectedAuthor).isEqualTo("Octavia Butler");
    }

    @Test
    public void xalanXPathApiAndCachedXPathApiEvaluateAgainstDom() throws Exception {
        Document document = parseXml(CATALOG_XML);
        CachedXPathAPI cachedXPath = new CachedXPathAPI();

        NodeList butlerBooks = cachedXPath.selectNodeList(document, "/catalog/book[@author = 'Octavia Butler']");
        assertThat(butlerBooks.getLength()).isEqualTo(2);
        assertThat(texts(cachedXPath.selectNodeList(document, "/catalog/book[@category = 'fiction']/title")))
                .containsExactly("Kindred", "Parable");

        XObject firstFantasyTitle = XPathAPI.eval(document, "string(/catalog/book[@category = 'fantasy']/title)");
        assertThat(firstFantasyTitle.str()).isEqualTo("Earthsea");

        XObject hasTwoCostlyBooks = cachedXPath.eval(document, "count(/catalog/book[number(price) >= 10]) = 2");
        assertThat(hasTwoCostlyBooks.bool()).isTrue();
    }

    @Test
    public void domLevelXPathEvaluatorUsesNamespaceResolversAndSnapshotResults() throws Exception {
        Document document = parseXml(NAMESPACED_CATALOG_XML);
        XPathEvaluatorImpl evaluator = new XPathEvaluatorImpl(document);
        XPathNSResolver resolver = evaluator.createNSResolver(document.getDocumentElement());

        XPathExpression fictionTitlesExpression = evaluator.createExpression(
                "/lib:catalog/lib:book[@meta:category = 'fiction']/lib:title", resolver);
        XPathResult fictionTitles = (XPathResult) fictionTitlesExpression.evaluate(
                document, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);

        assertThat(fictionTitles.getSnapshotLength()).isEqualTo(2);
        assertThat(fictionTitles.snapshotItem(0).getTextContent()).isEqualTo("Kindred");
        assertThat(fictionTitles.snapshotItem(1).getTextContent()).isEqualTo("Parable");

        XPathResult fantasyTitle = (XPathResult) evaluator.evaluate(
                "string(/lib:catalog/lib:book[@meta:id = 'b2']/lib:title)",
                document,
                resolver,
                XPathResult.STRING_TYPE,
                null);
        assertThat(fantasyTitle.getStringValue()).isEqualTo("Earthsea");
    }

    @Test
    public void exsltUtilityFunctionsOperateOnNodeSetsAndStrings() throws Exception {
        NodeList tokens = ExsltStrings.tokenize("alpha,beta,gamma", ",");
        assertThat(texts(tokens)).containsExactly("alpha", "beta", "gamma");
        assertThat(ExsltStrings.padding(5, "ab")).isEqualTo("ababa");

        Document document = parseXml(CATALOG_XML);
        NodeList prices = XPathAPI.selectNodeList(document, "/catalog/book/price");
        assertThat(ExsltMath.max(prices)).isEqualTo(15.0);
        assertThat(ExsltMath.min(prices)).isCloseTo(8.99, within(0.0001));

        NodeList titles = XPathAPI.selectNodeList(document, "/catalog/book/title");
        assertThat(ExsltStrings.concat(titles)).isEqualTo("KindredEarthseaParable");
    }

    @Test
    public void exsltSetFunctionsComputeRelationshipsBetweenNodeLists() throws Exception {
        Document document = parseXml(CATALOG_XML);
        NodeList books = document.getElementsByTagName("book");
        NodeSet allBooks = new NodeSet(books);
        NodeSet fictionBooks = nodeSet(books.item(0), books.item(2));
        NodeSet costlyBooks = nodeSet(books.item(1), books.item(2));

        assertThat(attributes(ExsltSets.intersection(fictionBooks, costlyBooks), "id")).containsExactly("b3");
        assertThat(attributes(ExsltSets.difference(allBooks, fictionBooks), "id")).containsExactly("b2");
        assertThat(attributes(ExsltSets.leading(allBooks, costlyBooks), "id")).containsExactly("b1");
        assertThat(attributes(ExsltSets.trailing(allBooks, costlyBooks), "id")).containsExactly("b3");
        assertThat(ExsltSets.hasSameNode(fictionBooks, costlyBooks)).isTrue();

        NodeSet categories = nodeSet(
                ((Element) books.item(0)).getAttributeNode("category"),
                ((Element) books.item(1)).getAttributeNode("category"),
                ((Element) books.item(2)).getAttributeNode("category"));
        assertThat(texts(ExsltSets.distinct(categories))).containsExactly("fiction", "fantasy");
    }

    private TransformerFactoryImpl newTransformerFactory() {
        TransformerFactoryImpl factory = new TransformerFactoryImpl();
        assertThat(factory.getFeature(StreamSource.FEATURE)).isTrue();
        assertThat(factory.getFeature(StreamResult.FEATURE)).isTrue();
        assertThat(factory.getFeature(DOMSource.FEATURE)).isTrue();
        assertThat(factory.getFeature(DOMResult.FEATURE)).isTrue();
        return factory;
    }

    private Object resolveXPathVariable(QName variableName) {
        return switch (variableName.getLocalPart()) {
            case "category" -> "fiction";
            case "minimumPrice" -> 10;
            default -> throw new IllegalArgumentException("Unexpected XPath variable: " + variableName);
        };
    }

    private static String transformToString(Transformer transformer, Source source) throws TransformerException {
        StringWriter writer = new StringWriter();
        transformer.transform(source, new StreamResult(writer));
        return writer.toString();
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(inputSource(xml, "memory:document.xml"));
    }

    private static StreamSource streamSource(String xml, String systemId) {
        StreamSource source = new StreamSource(new StringReader(xml));
        source.setSystemId(systemId);
        return source;
    }

    private static InputSource inputSource(String xml, String systemId) {
        InputSource source = new InputSource(new StringReader(xml));
        source.setSystemId(systemId);
        return source;
    }

    private static String textOf(Element element, String tagName) {
        return element.getElementsByTagName(tagName).item(0).getTextContent();
    }

    private static NodeSet nodeSet(Node... nodes) {
        NodeSet nodeSet = new NodeSet();
        for (Node node : nodes) {
            nodeSet.addNode(node);
        }
        return nodeSet;
    }

    private static List<String> attributes(NodeList nodes, String attributeName) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            values.add(element.getAttribute(attributeName));
        }
        return values;
    }

    private static List<String> texts(NodeList nodes) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            values.add(node.getTextContent());
        }
        return values;
    }

    private static final class InMemoryUriResolver implements URIResolver {
        private final Map<String, String> resources;

        private InMemoryUriResolver(Map<String, String> resources) {
            this.resources = resources;
        }

        @Override
        public Source resolve(String href, String base) throws TransformerException {
            String xml = resources.get(href);
            if (xml == null) {
                throw new TransformerException("Unexpected URI requested by stylesheet: " + href);
            }
            return streamSource(xml, "memory:" + href);
        }
    }
}
