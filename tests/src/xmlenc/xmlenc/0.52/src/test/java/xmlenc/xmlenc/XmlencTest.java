/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xmlenc.xmlenc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;
import org.znerd.xmlenc.InvalidXMLException;
import org.znerd.xmlenc.Library;
import org.znerd.xmlenc.LineBreak;
import org.znerd.xmlenc.XMLChecker;
import org.znerd.xmlenc.XMLEncoder;
import org.znerd.xmlenc.XMLEventListenerStates;
import org.znerd.xmlenc.XMLOutputter;
import org.znerd.xmlenc.sax.SAXEventReceiver;

public class XmlencTest {

    @Test
    void exposesLibraryVersionAndLineBreakConstants() {
        assertThat(Library.getVersion()).isEqualTo("0.52");
        assertThat(LineBreak.NONE.toString()).isEmpty();
        assertThat(LineBreak.UNIX.toString()).isEqualTo("\n");
        assertThat(LineBreak.DOS.toString()).isEqualTo("\r\n");
        assertThat(LineBreak.MACOS.toString()).isEqualTo("\r");
    }

    @Test
    void encoderWritesDeclarationsAndEscapesTextForXmlAndSevenBitEncodings() throws Exception {
        XMLEncoder utf8Encoder = XMLEncoder.getEncoder("UTF-8");
        StringWriter utf8Output = new StringWriter();

        utf8Encoder.declaration(utf8Output);
        utf8Encoder.text(utf8Output, "café <tag> & data", true);

        assertThat(utf8Encoder.getEncoding()).isEqualTo("UTF-8");
        assertThat(utf8Output.toString())
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>café &lt;tag&gt; &amp; data");

        XMLEncoder asciiEncoder = new XMLEncoder("US-ASCII");
        StringWriter asciiOutput = new StringWriter();
        asciiEncoder.text(asciiOutput, "snowman ☃ <&>", true);

        assertThat(asciiOutput.toString()).isEqualTo("snowman &#9731; &lt;&amp;&gt;");
    }

    @Test
    void encoderHandlesAttributeQuotingAndOptionalAmpersandEscaping() throws Exception {
        XMLEncoder encoder = new XMLEncoder("UTF-8");
        StringWriter doubleQuoted = new StringWriter();
        StringWriter singleQuoted = new StringWriter();
        StringWriter ampersandPreserved = new StringWriter();

        encoder.attribute(doubleQuoted, "title", "Tom \"Jerry\" & friends", '"', true);
        encoder.attribute(singleQuoted, "title", "Tom 'Jerry' & \"Spike\"", '\'', true);
        encoder.attribute(ampersandPreserved, "href", "chapter?a=1&b=2", '"', false);

        assertThat(doubleQuoted.toString()).isEqualTo(" title=\"Tom &quot;Jerry&quot; &amp; friends\"");
        assertThat(singleQuoted.toString()).isEqualTo(" title='Tom &apos;Jerry&apos; &amp; \"Spike\"'");
        assertThat(ampersandPreserved.toString()).isEqualTo(" href=\"chapter?a=1&b=2\"");
    }

    @Test
    void outputterCreatesCompactDocumentsAndTracksState() throws Exception {
        StringWriter writer = new StringWriter();
        XMLOutputter outputter = new XMLOutputter(writer, "UTF-8");

        assertThat(outputter.getWriter()).isSameAs(writer);
        assertThat(outputter.getEncoding()).isEqualTo("UTF-8");
        assertThat(outputter.getState()).isSameAs(XMLEventListenerStates.BEFORE_XML_DECLARATION);

        outputter.startTag("root");
        outputter.setQuotationMark('\'');
        outputter.attribute("title", "Tom's \"quoted\" & <escaped>");
        assertThat(outputter.getElementStackSize()).isEqualTo(1);
        assertThat(outputter.getElementStack()).containsExactly("root");
        assertThat(outputter.getState()).isSameAs(XMLEventListenerStates.START_TAG_OPEN);

        outputter.startTag("child");
        outputter.pcdata("A < B & C > D");
        outputter.endTag();
        outputter.cdata("raw <xml> & data");
        outputter.endTag();
        outputter.endDocument();

        assertThat(writer.toString()).isEqualTo("<root title='Tom&apos;s \"quoted\" &amp; &lt;escaped&gt;'>"
                + "<child>A &lt; B &amp; C &gt; D</child><![CDATA[raw <xml> & data]]></root>");
        assertThat(outputter.getState()).isSameAs(XMLEventListenerStates.DOCUMENT_ENDED);
        assertThat(outputter.getElementStackSize()).isZero();
    }

    @Test
    void outputterWritesDocumentTypeDeclarationsWithExternalIdentifiers() throws Exception {
        StringWriter systemWriter = new StringWriter();
        XMLOutputter systemOutputter = new XMLOutputter(systemWriter, "UTF-8");

        systemOutputter.dtd("catalog", null, "catalog.dtd");

        assertThat(systemWriter.toString()).isEqualTo("<!DOCTYPE catalog SYSTEM \"catalog.dtd\">");
        assertThat(systemOutputter.getState()).isSameAs(XMLEventListenerStates.BEFORE_ROOT_ELEMENT);

        StringWriter publicWriter = new StringWriter();
        XMLOutputter publicOutputter = new XMLOutputter(publicWriter, "UTF-8");

        publicOutputter.dtd("html", "-//W3C//DTD XHTML 1.0 Strict//EN", "xhtml1-strict.dtd");

        assertThat(publicWriter.toString())
                .isEqualTo("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"xhtml1-strict.dtd\">");
        assertThat(publicOutputter.getState()).isSameAs(XMLEventListenerStates.BEFORE_ROOT_ELEMENT);
    }

    @Test
    void outputterFormatsNestedElementsAndClosesOpenElements() throws Exception {
        StringWriter writer = new StringWriter();
        XMLOutputter outputter = new XMLOutputter(writer, "UTF-8");

        outputter.setLineBreak(LineBreak.UNIX);
        outputter.setIndentation("  ");
        outputter.declaration();
        outputter.comment("generated safely");
        outputter.startTag("root");
        outputter.pi("xml-stylesheet", "type=\"text/xsl\" href=\"style.xsl\"");
        outputter.startTag("empty");
        outputter.close();

        assertThat(writer.toString()).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!--generated safely-->\n\n"
                + "<root><?xml-stylesheet type=\"text/xsl\" href=\"style.xsl\"?>\n"
                + "  <empty/>\n"
                + "</root>");
        assertThat(outputter.getState()).isSameAs(XMLEventListenerStates.AFTER_ROOT_ELEMENT);
    }

    @Test
    void outputterResumesWritingFromExplicitStateAndElementStack() throws Exception {
        StringWriter writer = new StringWriter();
        writer.write("<root><parent");
        XMLOutputter outputter = new XMLOutputter(writer, "UTF-8");

        outputter.setState(XMLEventListenerStates.START_TAG_OPEN, new String[] {"root", "parent"});
        outputter.attribute("id", "p1");
        outputter.startTag("child");
        outputter.pcdata("resumed");
        outputter.endTag();
        outputter.endDocument();

        assertThat(writer.toString()).isEqualTo("<root><parent id=\"p1\"><child>resumed</child></parent></root>");
        assertThat(outputter.getState()).isSameAs(XMLEventListenerStates.DOCUMENT_ENDED);
        assertThat(outputter.getElementStack()).isNull();
    }

    @Test
    void xmlCheckerValidatesXmlProductions() {
        XMLChecker.checkS(" \t\r\n");
        XMLChecker.checkName("ns:element-1");
        XMLChecker.checkSystemLiteral("\"schema/location.dtd\"");
        XMLChecker.checkPubidLiteral("\"-//Example//DTD Sample 1.0//EN\"");

        assertThat(XMLChecker.isName("_valid.name")).isTrue();
        assertThat(XMLChecker.isName("1invalid")).isFalse();
        assertThat(XMLChecker.isSystemLiteral("\"schema.dtd\"")).isTrue();
        assertThat(XMLChecker.isSystemLiteral("schema.dtd")).isFalse();
        assertThat(XMLChecker.isPubidLiteral("\"ABC 123 +,./:=?;!*#@$_%-\"")).isTrue();
        assertThat(XMLChecker.isPubidLiteral("\"contains[bracket]\"")).isFalse();

        assertThatExceptionOfType(InvalidXMLException.class).isThrownBy(() -> XMLChecker.checkS("not whitespace"));
        assertThatExceptionOfType(InvalidXMLException.class).isThrownBy(() -> XMLChecker.checkName("bad name"));
    }

    @Test
    void saxEventReceiverStreamsSaxEventsToOutputter() throws Exception {
        StringWriter writer = new StringWriter();
        XMLOutputter outputter = new XMLOutputter(writer, "UTF-8");
        SAXEventReceiver receiver = new SAXEventReceiver(outputter);
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "id", "id", "CDATA", "b1 & b2");
        char[] title = "XML & Native".toCharArray();

        receiver.startDocument();
        receiver.startElement("", "book", "book", attributes);
        receiver.startElement("", "title", "title", new AttributesImpl());
        receiver.characters(title, 0, title.length);
        receiver.endElement("", "title", "title");
        receiver.endElement("", "book", "book");
        receiver.endDocument();

        assertThat(writer.toString()).isEqualTo("<book id=\"b1 &amp; b2\"><title>XML &amp; Native</title></book>");
        assertThat(outputter.getState()).isSameAs(XMLEventListenerStates.DOCUMENT_ENDED);
    }

    @Test
    void publicApisRejectInvalidStateAndInput() throws Exception {
        XMLOutputter uninitialized = new XMLOutputter();
        XMLOutputter outputter = new XMLOutputter(new StringWriter(), "UTF-8");

        assertThatThrownBy(uninitialized::declaration).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> outputter.attribute("id", "1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> outputter.startTag(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outputter.setQuotationMark('`')).isInstanceOf(IllegalArgumentException.class);
        outputter.startTag("root");
        assertThatThrownBy(() -> outputter.pcdata("illegal " + Character.toString((char) 1) + " character"))
                .isInstanceOf(InvalidXMLException.class);
        assertThatThrownBy(() -> new XMLEncoder("UTF-32"))
                .isInstanceOf(UnsupportedEncodingException.class);
        assertThatThrownBy(() -> new SAXEventReceiver(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
