/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_xml

import groovy.namespace.QName
import groovy.util.Node
import groovy.xml.DOMBuilder
import groovy.xml.Entity
import groovy.xml.FactorySupport
import groovy.xml.MarkupBuilder
import groovy.xml.Namespace
import groovy.xml.NamespaceBuilder
import groovy.xml.SAXBuilder
import groovy.xml.StaxBuilder
import groovy.xml.StreamingDOMBuilder
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.StreamingSAXBuilder
import groovy.xml.XmlNodePrinter
import groovy.xml.XmlParser
import groovy.xml.XmlSlurper
import groovy.xml.XmlUtil
import groovy.xml.dom.DOMCategory
import groovy.xml.slurpersupport.GPathResult
import org.apache.groovy.xml.tools.DomToGroovy
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

import javax.xml.XMLConstants
import javax.xml.parsers.SAXParser
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter
import javax.xml.transform.stream.StreamSource

import static org.assertj.core.api.Assertions.assertThat

public class Groovy_xmlTest {
    private static final String CATALOG_XML = '''\
            <catalog region="EU">
              <book id="b1" category="programming">
                <title>Groovy &amp; XML</title>
                <author>Apache Groovy</author>
              </book>
              <book id="b2" category="reference">
                <title>Native Image Guide</title>
                <author>GraalVM</author>
              </book>
            </catalog>
            '''.stripIndent()

    @Test
    void xmlSlurperNavigatesAttributesTextAndNamespaces() {
        XmlSlurper slurper = new XmlSlurper(false, true)
        GPathResult catalog = slurper.parseText(CATALOG_XML)

        assertThat(catalog.getProperty('@region').text()).isEqualTo('EU')
        assertThat(catalog.book.find { it.getProperty('@id').text() == 'b1' }.title.text()).isEqualTo('Groovy & XML')
        assertThat(catalog.book.collect { it.getProperty('@category').text() }).containsExactly('programming', 'reference')

        GPathResult namespaced = new XmlSlurper(false, true)
                .parseText('''\
                        <bk:catalog xmlns:bk="urn:books">
                          <bk:book id="b1"><bk:title>Groovy</bk:title></bk:book>
                          <bk:book id="b2"><bk:title>GraalVM</bk:title></bk:book>
                        </bk:catalog>
                        '''.stripIndent())
                .declareNamespace(bk: 'urn:books')

        assertThat(namespaced.getProperty('bk:book').find { it.getProperty('@id').text() == 'b2' }
                .getProperty('bk:title').text()).isEqualTo('GraalVM')
    }

    @Test
    void xmlParserBuildsMutableNodeTrees() {
        XmlParser parser = new XmlParser(false, true)
        parser.trimWhitespace = true
        Node catalog = parser.parseText(CATALOG_XML)

        catalog.appendNode('book', [id: 'b3', category: 'testing'])
                .appendNode('title', 'Reachability Metadata')

        assertThat(catalog.name()).isEqualTo('catalog')
        assertThat(catalog.attribute('region')).isEqualTo('EU')
        assertThat(catalog.book.collect { it.attribute('id') }).containsExactly('b1', 'b2', 'b3')
        assertThat(catalog.book.find { it.attribute('id') == 'b3' }.title.text()).isEqualTo('Reachability Metadata')
    }

    @Test
    void markupBuilderEmitsEscapedMarkupCommentsProcessingInstructionsAndEntities() {
        StringWriter writer = new StringWriter()
        MarkupBuilder builder = new MarkupBuilder(writer)
        builder.doubleQuotes = true
        builder.omitNullAttributes = true
        builder.omitEmptyAttributes = true
        builder.expandEmptyElements = true

        builder.records(empty: '', missing: null, region: 'EU') {
            mkp.comment('generated catalog')
            mkp.pi('xml-stylesheet': [type: 'text/xsl', href: 'catalog.xsl'])
            book(id: 'b1', category: 'programming') {
                title('Groovy & XML')
                description {
                    mkp.'yield'('Fast ')
                    mkp.yieldUnescaped('&copy;')
                    mkp.'yield'(' safe')
                }
                mkp.yieldUnescaped('<raw enabled="true"/>')
                emptyElement()
            }
        }

        String xml = writer.toString()
        assertThat(xml).contains('<records region="EU">')
        assertThat(xml).doesNotContain('empty=').doesNotContain('missing=')
        assertThat(xml).contains('<!-- generated catalog -->')
        assertThat(xml).contains('<?xml-stylesheet type="text/xsl" href="catalog.xsl"?>')
        assertThat(xml).contains('<title>Groovy &amp; XML</title>')
        assertThat(xml).contains('Fast &copy; safe')
        assertThat(xml).contains('<raw enabled="true"/>')
        assertThat(xml).contains('<emptyElement></emptyElement>')
    }

    @Test
    void namespaceBuilderAppliesPrefixesToMarkupBuilderOutput() {
        StringWriter writer = new StringWriter()
        MarkupBuilder markupBuilder = new MarkupBuilder(writer)
        def books = NamespaceBuilder.newInstance(markupBuilder, 'urn:books', 'bk')

        books.catalog(region: 'EU') {
            book(id: 'b1') {
                title('Groovy')
            }
        }

        String xml = writer.toString()
        assertThat(xml).contains("<bk:catalog region='EU'>")
        assertThat(xml).contains("<bk:book id='b1'>")
        assertThat(xml).contains('<bk:title>Groovy</bk:title>')
    }

    @Test
    void namespaceCreatesQualifiedNamesUsedByParserNodes() {
        Namespace namespace = new Namespace('urn:books', 'bk')
        QName bookName = namespace.book
        QName titleName = namespace.get('title')

        Node node = new Node(null, bookName, [id: 'b1'])
        node.appendNode(titleName, 'Groovy')

        assertThat(bookName.namespaceURI).isEqualTo('urn:books')
        assertThat(bookName.localPart).isEqualTo('book')
        assertThat(bookName.prefix).isEqualTo('bk')
        assertThat(node.name()).isEqualTo(bookName)
        assertThat(node.children().first().name()).isEqualTo(titleName)
        assertThat(node.children().first().text()).isEqualTo('Groovy')
    }

    @Test
    void streamingMarkupBuilderProducesWritableXmlWithDeclarationsAndNamespaces() {
        StreamingMarkupBuilder builder = new StreamingMarkupBuilder()
        builder.useDoubleQuotes = true
        builder.expandEmptyElements = true

        String xml = builder.bind {
            mkp.xmlDeclaration()
            mkp.comment('streamed catalog')
            catalog(xmlns: 'urn:books') {
                book(id: 'b1') {
                    title('Groovy')
                    rights(Entity.copy)
                    empty()
                }
            }
        }.toString()

        assertThat(xml).contains('<?xml version=')
        assertThat(xml).contains('streamed catalog')
        assertThat(xml).contains('<catalog xmlns="urn:books">')
        assertThat(xml).contains('<book id="b1"><title>Groovy</title><rights>&copy;</rights><empty></empty></book>')
    }

    @Test
    void xmlUtilSerializesNodesGPathResultsWritableMarkupAndDomElements() {
        Node node = new XmlParser(false, true).parseText('<records><record id="b1">Groovy</record></records>')
        GPathResult result = new XmlSlurper(false, true).parseText('<records><record id="b2">GraalVM</record></records>')
        Document document = DOMBuilder.parse(new StringReader('<records><record id="b3">Native</record></records>'))
        def writable = new StreamingMarkupBuilder().bind { records { entry(id: 'b4', 'Streaming') } }

        String nodeXml = XmlUtil.serialize(node)
        String resultXml = XmlUtil.serialize(result)
        String elementXml = XmlUtil.serialize(document.documentElement, false)
        String writableXml = XmlUtil.serialize(writable)
        String stringXml = XmlUtil.serialize('<records><record id="b5">String</record></records>', false)
        String escaped = XmlUtil.escapeXml('<record id="b6">Groovy & XML</record>')

        assertThat(nodeXml).contains('<record id="b1">Groovy</record>')
        assertThat(resultXml).contains('<record id="b2">GraalVM</record>')
        assertThat(elementXml).contains('<record id="b3">Native</record>')
        assertThat(writableXml).contains('<entry id="b4">Streaming</entry>')
        assertThat(stringXml).contains('<record id="b5">String</record>')
        assertThat(escaped).contains('&lt;record')
        assertThat(escaped).contains('Groovy &amp; XML')
        assertThat(escaped).contains('&lt;/record&gt;')
    }

    @Test
    void xmlNodePrinterRendersParserNodesWithConfiguredFormatting() {
        Node node = new XmlParser(false, true).parseText('<records><record id="b1"><title>Groovy</title></record></records>')
        StringWriter writer = new StringWriter()
        XmlNodePrinter printer = new XmlNodePrinter(new PrintWriter(writer), '  ')
        printer.quote = '"'
        printer.expandEmptyElements = true
        printer.preserveWhitespace = true

        printer.print(node)

        String xml = writer.toString()
        assertThat(xml).contains('<records>')
        assertThat(xml).contains('<record id="b1">')
        assertThat(xml).contains('<title>Groovy</title>')
    }

    @Test
    void domBuilderParsesDocumentsAndDomCategoryNavigatesThem() {
        Document document = DOMBuilder.parse(new StringReader(CATALOG_XML), false, true)

        assertThat(document.documentElement.nodeName).isEqualTo('catalog')
        assertThat(document.documentElement.getAttribute('region')).isEqualTo('EU')

        use(DOMCategory) {
            assertThat(document.documentElement.book[0].title.text()).isEqualTo('Groovy & XML')
            assertThat(document.documentElement.book[1]['@id']).isEqualTo('b2')
        }

        String serialized = XmlUtil.serialize(document.documentElement, false)
        assertThat(serialized).contains('<title>Groovy &amp; XML</title>')
    }

    @Test
    void streamingDomBuilderCreatesDomDocumentsFromMarkupClosures() {
        Document document = new StreamingDOMBuilder().bind {
            catalog(region: 'EU') {
                book(id: 'b1') {
                    title('Groovy')
                }
            }
        }.call() as Document

        assertThat(document.documentElement.nodeName).isEqualTo('catalog')
        assertThat(document.documentElement.getAttribute('region')).isEqualTo('EU')
        assertThat(document.getElementsByTagName('title').item(0).textContent).isEqualTo('Groovy')
    }

    @Test
    void saxBuilderStreamsMarkupToContentHandler() {
        RecordingHandler handler = new RecordingHandler()
        SAXBuilder builder = new SAXBuilder(handler)

        builder.catalog(region: 'EU') {
            book(id: 'b1') {
                title('Groovy')
            }
        }

        assertThat(handler.events).contains(
                'start:catalog:EU',
                'start:book:b1',
                'start:title:',
                'text:Groovy',
                'end:title',
                'end:book',
                'end:catalog')
    }

    @Test
    void streamingSaxBuilderStreamsBoundMarkupClosuresToContentHandler() {
        RecordingHandler handler = new RecordingHandler()
        def eventSource = new StreamingSAXBuilder().bind {
            catalog(region: 'EU') {
                book(id: 'b1') {
                    title('Groovy')
                }
            }
        }

        eventSource.call(handler)

        assertThat(handler.events).contains(
                'start:catalog:EU',
                'start:book:b1',
                'start:title:',
                'end:title',
                'end:book',
                'end:catalog')
        assertThat(handler.text()).isEqualTo('Groovy')
    }

    @Test
    void staxBuilderWritesMarkupToXmlStreamWriter() {
        StringWriter writer = new StringWriter()
        XMLStreamWriter xmlStreamWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(writer)
        try {
            StaxBuilder builder = new StaxBuilder(xmlStreamWriter)
            builder.catalog(region: 'EU') {
                book(id: 'b1') {
                    title('Groovy')
                }
            }
            xmlStreamWriter.flush()
        } finally {
            xmlStreamWriter.close()
        }

        String xml = writer.toString()
        assertThat(xml).contains('<catalog')
        assertThat(xml).contains('region="EU"')
        assertThat(xml).contains('<book')
        assertThat(xml).contains('id="b1"')
        assertThat(xml).contains('<title>Groovy</title>')
    }

    @Test
    void factorySupportCreatesJaxpFactoriesUsableByXmlParsers() {
        Document document = FactorySupport.createDocumentBuilderFactory()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader('<records><record id="b1"/></records>')))
        RecordingHandler handler = new RecordingHandler()
        FactorySupport.createSaxParserFactory()
                .newSAXParser()
                .parse(new InputSource(new StringReader('<records><record id="b2">Groovy</record></records>')), handler)

        assertThat(document.documentElement.nodeName).isEqualTo('records')
        assertThat(document.getElementsByTagName('record').item(0).attributes.getNamedItem('id').nodeValue).isEqualTo('b1')
        assertThat(handler.events).contains('start:record:b2', 'text:Groovy')
    }

    @Test
    void xmlUtilCreatesSchemaValidatingSaxParsers() {
        String schema = '''\
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="catalog">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="book" maxOccurs="unbounded">
                          <xs:complexType>
                            <xs:sequence>
                              <xs:element name="title" type="xs:string"/>
                            </xs:sequence>
                            <xs:attribute name="id" type="xs:ID" use="required"/>
                          </xs:complexType>
                        </xs:element>
                      </xs:sequence>
                      <xs:attribute name="region" type="xs:string" use="required"/>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                '''.stripIndent()
        SAXParser parser = XmlUtil.newSAXParser(
                XMLConstants.W3C_XML_SCHEMA_NS_URI,
                new StreamSource(new StringReader(schema)))
        RecordingHandler handler = new RecordingHandler()

        parser.parse(new InputSource(new StringReader('''\
                <catalog region="EU">
                  <book id="b1"><title>Groovy</title></book>
                  <book id="b2"><title>GraalVM</title></book>
                </catalog>
                '''.stripIndent())), handler)

        assertThat(handler.events).contains(
                'start:catalog:EU',
                'start:book:b1',
                'text:Groovy',
                'start:book:b2',
                'text:GraalVM',
                'end:catalog')
    }

    @Test
    void domToGroovyPrintsBuilderSourceForDomDocuments() {
        Document document = DOMBuilder.parse(new StringReader('<records><record id="b1"><title>Groovy</title></record></records>'))
        StringWriter writer = new StringWriter()
        DomToGroovy printer = new DomToGroovy(new PrintWriter(writer))

        printer.print(document)

        String source = writer.toString()
        assertThat(source).contains('records')
        assertThat(source).contains('record')
        assertThat(source).contains('title')
        assertThat(source).contains('Groovy')
    }

    private static final class RecordingHandler extends DefaultHandler {
        final List<String> events = []

        @Override
        void startElement(String uri, String localName, String qName, Attributes attributes) {
            String elementName = localName ?: qName
            String id = attributes.getValue('id') ?: attributes.getValue('region') ?: ''
            events.add("start:${elementName}:${id}".toString())
        }

        @Override
        void characters(char[] ch, int start, int length) {
            String text = new String(ch, start, length).trim()
            if (!text.isEmpty()) {
                events.add("text:${text}".toString())
            }
        }

        @Override
        void endElement(String uri, String localName, String qName) {
            String elementName = localName ?: qName
            events.add("end:${elementName}".toString())
        }

        String text() {
            events.findAll { it.startsWith('text:') }
                    .collect { it.substring('text:'.length()) }
                    .join()
        }
    }
}
