/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_kxml.kxml2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

public class XmlPullParserFactoryTest {
    private static final String PARSER_CLASS_NAME = "org.kxml2.io.KXmlParser";
    private static final String SERIALIZER_CLASS_NAME = "org.kxml2.io.KXmlSerializer";
    private static final String SERVICE_PROVIDER_CLASS_NAMES = PARSER_CLASS_NAME + "," + SERIALIZER_CLASS_NAME;

    @Test
    void defaultFactoryReadsServiceResourceAndCreatesConfiguredImplementations() throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);

        XmlPullParser parser = factory.newPullParser();
        assertThat(parser.getClass().getName()).isEqualTo(PARSER_CLASS_NAME);
        assertThat(parser.getFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES)).isTrue();
        assertThatParsedNamespacedDocument(parser);

        XmlSerializer serializer = factory.newSerializer();
        assertThat(serializer.getClass().getName()).isEqualTo(SERIALIZER_CLASS_NAME);
        assertThatSerializedDocument(serializer);
    }

    @Test
    void explicitProviderListCreatesParserAndSerializerWithoutServiceLookup() throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance(SERVICE_PROVIDER_CLASS_NAMES, null);

        XmlPullParser parser = factory.newPullParser();
        assertThat(parser.getClass().getName()).isEqualTo(PARSER_CLASS_NAME);
        assertThatParsedSimpleDocument(parser);

        XmlSerializer serializer = factory.newSerializer();
        assertThat(serializer.getClass().getName()).isEqualTo(SERIALIZER_CLASS_NAME);
        assertThatSerializedDocument(serializer);
    }

    private static void assertThatParsedNamespacedDocument(XmlPullParser parser) throws Exception {
        parser.setInput(new StringReader("<ns:greeting xmlns:ns=\"urn:test\" language=\"en\">hello</ns:greeting>"));

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        assertThat(parser.getNamespace()).isEqualTo("urn:test");
        assertThat(parser.getName()).isEqualTo("greeting");
        assertThat(parser.getAttributeValue(null, "language")).isEqualTo("en");
        assertThat(parser.nextText()).isEqualTo("hello");
        assertThat(parser.next()).isEqualTo(XmlPullParser.END_DOCUMENT);
    }

    private static void assertThatParsedSimpleDocument(XmlPullParser parser) throws Exception {
        parser.setInput(new StringReader("<greeting language=\"en\">hello</greeting>"));

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        assertThat(parser.getName()).isEqualTo("greeting");
        assertThat(parser.getAttributeValue(null, "language")).isEqualTo("en");
        assertThat(parser.nextText()).isEqualTo("hello");
        assertThat(parser.next()).isEqualTo(XmlPullParser.END_DOCUMENT);
    }

    private static void assertThatSerializedDocument(XmlSerializer serializer) throws Exception {
        StringWriter writer = new StringWriter();

        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", Boolean.TRUE);
        serializer.startTag(XmlPullParser.NO_NAMESPACE, "greeting");
        serializer.attribute(XmlPullParser.NO_NAMESPACE, "language", "en");
        serializer.text("hello");
        serializer.endTag(XmlPullParser.NO_NAMESPACE, "greeting");
        serializer.endDocument();
        serializer.flush();

        assertThat(writer.toString())
                .contains("<greeting")
                .contains("language=\"en\"")
                .contains(">hello</greeting>");
    }
}
