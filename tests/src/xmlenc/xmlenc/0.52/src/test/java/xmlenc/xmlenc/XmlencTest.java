/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xmlenc.xmlenc;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;
import org.znerd.xmlenc.InvalidXMLException;
import org.znerd.xmlenc.Library;
import org.znerd.xmlenc.LineBreak;
import org.znerd.xmlenc.XMLChecker;
import org.znerd.xmlenc.XMLEncoder;
import org.znerd.xmlenc.XMLEventListenerStates;
import org.znerd.xmlenc.XMLOutputter;
import org.znerd.xmlenc.sax.SAXEventReceiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class XmlencTest {
    @Test
    void exposesVersionAndValueConstants() {
        XMLOutputter outputter = new XMLOutputter();

        assertThat(Library.getVersion()).isEqualTo("0.52");
        assertThat(LineBreak.NONE.toString()).isEmpty();
        assertThat(LineBreak.UNIX.toString()).isEqualTo("\n");
        assertThat(LineBreak.DOS.toString()).isEqualTo("\r\n");
        assertThat(LineBreak.MACOS.toString()).isEqualTo("\r");
        assertThat(XMLEventListenerStates.UNINITIALIZED.toString()).isEqualTo("UNINITIALIZED");
        assertThat(outputter.getState()).isSameAs(XMLEventListenerStates.UNINITIALIZED);
        assertThat(outputter.getElementStack()).isNull();
        assertThat(outputter.getQuotationMark()).isEqualTo('"');
        assertThat(outputter.isEscaping()).isTrue();
    }

    @Test
    void encoderWritesDeclarationAndEscapesTextForUtf8() throws Exception {
        XMLEncoder encoder = XMLEncoder.getEncoder("UTF-8");
        StringWriter writer = new StringWriter();

        encoder.declaration(writer);
        encoder.text(writer, "<tag attr=\"value\">Tom & Jerry's café</tag>", true);

        assertThat(encoder.getEncoding()).isEqualTo("UTF-8");
        assertThat(writer.toString())
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "&lt;tag attr=\"value\"&gt;Tom &amp; Jerry's café&lt;/tag&gt;");
    }

    @Test
    void encoderCanLeaveAmpersandsUnescapedAndUsesCharacterReferencesForSevenBitEncodings() throws Exception {
        XMLEncoder encoder = new XMLEncoder("US-ASCII");
        StringWriter writer = new StringWriter();

        encoder.text(writer, "already &amp; escaped café", false);
        encoder.text(writer, '<', true);
        encoder.text(writer, 'é', true);

        assertThat(writer.toString()).isEqualTo("already &amp; escaped caf&#233;&lt;&#233;");
    }

    @Test
    void encoderEscapesAttributeValuesWithConfiguredQuotationMarks() throws Exception {
        XMLEncoder encoder = new XMLEncoder("ISO-8859-1");
        StringWriter writer = new StringWriter();

        encoder.attribute(writer, "double", "Tom & \"Jerry\" <cartoon>", '"', true);
        encoder.attribute(writer, "single", "it's > fine", '\'', true);

        assertThat(writer.toString()).isEqualTo(
                " double=\"Tom &amp; &quot;Jerry&quot; &lt;cartoon&gt;\" single='it&apos;s &gt; fine'");
    }

    @Test
    void encoderRejectsUnsupportedEncodingsAndInvalidXmlCharacters() throws Exception {
        XMLEncoder encoder = new XMLEncoder("UTF-8");
        StringWriter writer = new StringWriter();

        assertThatExceptionOfType(UnsupportedEncodingException.class)
                .isThrownBy(() -> new XMLEncoder("UTF-32"));
        assertThatExceptionOfType(InvalidXMLException.class)
                .isThrownBy(() -> encoder.text(writer, '\u0001', true))
                .withMessageContaining("0x1");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> encoder.attribute(writer, "bad", "value", '`', true));
    }

    @Test
    void checkerValidatesNamesWhitespaceAndLiterals() {
        char[] nameBuffer = ":element-42".toCharArray();
        char[] whitespaceBuffer = " \t\r\n".toCharArray();

        XMLChecker.checkName(nameBuffer, 0, nameBuffer.length);
        XMLChecker.checkS(whitespaceBuffer, 0, whitespaceBuffer.length);
        XMLChecker.checkSystemLiteral("'schema/example.dtd'");
        XMLChecker.checkPubidLiteral("\"-//Example//DTD Test 1.0//EN\"");

        assertThat(XMLChecker.isName("ns:element-42")).isTrue();
        assertThat(XMLChecker.isName("1element")).isFalse();
        assertThat(XMLChecker.isSystemLiteral("\"schema/example.dtd\"")).isTrue();
        assertThat(XMLChecker.isPubidLiteral("\"bad<public-id\"")).isFalse();
        assertThatExceptionOfType(InvalidXMLException.class).isThrownBy(() -> XMLChecker.checkS("not whitespace"));
        assertThatExceptionOfType(InvalidXMLException.class).isThrownBy(() -> XMLChecker.checkName(""));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> XMLChecker.checkName("root".toCharArray(), 0, 5));
    }

    @Test
    void outputterWritesExternalDoctypeBeforeRootElement() throws Exception {
        StringWriter writer = new StringWriter();
        XMLOutputter outputter = new XMLOutputter(writer, "UTF-8");

        outputter.setLineBreak(LineBreak.UNIX);
        outputter.declaration();
        outputter.dtd("catalog", "-//Example//DTD Catalog//EN", "catalog.dtd");
        assertThat(outputter.getState()).isSameAs(XMLEventListenerStates.BEFORE_ROOT_ELEMENT);

        outputter.startTag("catalog");
        outputter.endDocument();

        assertThat(writer.toString()).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE catalog PUBLIC \"-//Example//DTD Catalog//EN\" \"catalog.dtd\">\n"
                + "<catalog/>");
    }

    @Test
    void outputterBuildsCompleteEscapedDocumentAndTracksState() throws Exception {
        StringWriter writer = new StringWriter();
        XMLOutputter outputter = new XMLOutputter(writer, "UTF-8");

        outputter.declaration();
        outputter.startTag("root");
        outputter.attribute("id", "r1 & \"quoted\"");
        assertThat(outputter.getElementStack()).containsExactly("root");
        assertThat(outputter.getState()).isSameAs(XMLEventListenerStates.START_TAG_OPEN);

        outputter.startTag("child");
        outputter.pcdata("A < B & C > D");
        outputter.endTag();
        outputter.cdata("<literal>&data");
        outputter.comment("safe comment");
        outputter.pi("xml-stylesheet", "href=\"style.css\"");
        outputter.endDocument();

        assertThat(outputter.getState()).isSameAs(XMLEventListenerStates.DOCUMENT_ENDED);
        assertThat(outputter.getElementStack()).isNull();
        assertThat(writer.toString()).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<root id=\"r1 &amp; &quot;quoted&quot;\">"
                + "<child>A &lt; B &amp; C &gt; D</child>"
                + "<![CDATA[<literal>&data]]>"
                + "<!--safe comment-->"
                + "<?xml-stylesheet href=\"style.css\"?>"
                + "</root>");
    }

    @Test
    void outputterSupportsPrettyPrintingStackCapacityAndExplicitState() throws Exception {
        StringWriter writer = new StringWriter();
        XMLOutputter outputter = new XMLOutputter(writer, new XMLEncoder("UTF-8"));

        outputter.setLineBreak(LineBreak.UNIX);
        outputter.setIndentation("  ");
        outputter.setQuotationMark('\'');
        outputter.setElementStackCapacity(32);
        outputter.startTag("root");
        outputter.attribute("kind", "nested");
        outputter.startTag("child");
        outputter.endDocument();

        assertThat(outputter.getEncoding()).isEqualTo("UTF-8");
        assertThat(outputter.getLineBreak()).isSameAs(LineBreak.UNIX);
        assertThat(outputter.getIndentation()).isEqualTo("  ");
        assertThat(outputter.getQuotationMark()).isEqualTo('\'');
        assertThat(outputter.getElementStackCapacity()).isGreaterThanOrEqualTo(32);
        assertThat(writer.toString()).isEqualTo("<root kind='nested'>\n  <child/>\n</root>");

        outputter.setState(XMLEventListenerStates.START_TAG_OPEN, new String[] { "root", "leaf" });
        assertThat(outputter.getElementStackSize()).isEqualTo(2);
        assertThat(outputter.getElementStack()).containsExactly("root", "leaf");
        outputter.setState(XMLEventListenerStates.UNINITIALIZED, null);
        assertThat(outputter.getState()).isSameAs(XMLEventListenerStates.UNINITIALIZED);
        assertThat(outputter.getWriter()).isNull();
    }

    @Test
    void outputterEnforcesEventOrderingAndConfigurationContracts() throws Exception {
        StringWriter writer = new StringWriter();
        XMLOutputter outputter = new XMLOutputter(writer, "UTF-8");

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> outputter.attribute("name", "value"));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(outputter::endTag);
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> outputter.setIndentation("  "));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> outputter.setQuotationMark('`'));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> outputter.setState(null, null));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> outputter.setState(XMLEventListenerStates.START_TAG_OPEN, null));

        outputter.setLineBreak(LineBreak.DOS);
        outputter.setIndentation("\t");
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> outputter.setIndentation("\n"));

        outputter.startTag("root");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> outputter.attribute(null, "value"));
    }

    @Test
    void saxEventReceiverStreamsParserEventsToOutputter() throws Exception {
        StringReader source = new StringReader("<root attr='Tom &amp; Jerry'><child>A &lt; B</child></root>");
        StringWriter writer = new StringWriter();
        XMLOutputter outputter = new XMLOutputter(writer, "UTF-8");
        SAXEventReceiver receiver = new SAXEventReceiver(outputter);
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();

        parser.parse(new InputSource(source), new ForwardingHandler(receiver));

        assertThat(writer.toString()).isEqualTo("<root attr=\"Tom &amp; Jerry\"><child>A &lt; B</child></root>");
    }

    private static final class ForwardingHandler extends DefaultHandler {
        private final SAXEventReceiver receiver;

        private ForwardingHandler(SAXEventReceiver receiver) {
            this.receiver = receiver;
        }

        @Override
        public void startDocument() throws org.xml.sax.SAXException {
            receiver.startDocument();
        }

        @Override
        public void endDocument() throws org.xml.sax.SAXException {
            receiver.endDocument();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws org.xml.sax.SAXException {
            receiver.startElement(uri, localName, qName, attributes);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws org.xml.sax.SAXException {
            receiver.endElement(uri, localName, qName);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws org.xml.sax.SAXException {
            receiver.characters(ch, start, length);
        }
    }
}
