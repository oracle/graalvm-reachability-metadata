/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xpp3.xpp3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.xmlpull.mxp1.MXParser;
import org.xmlpull.mxp1.MXParserFactory;
import org.xmlpull.mxp1_serializer.MXSerializer;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

public class XmlPullParserFactoryTest {
    @Test
    void defaultFactoryDiscoveryCreatesParserAndSerializer() throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);

        assertThat(factory).isInstanceOf(MXParserFactory.class);

        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader("<root xmlns=\"urn:test\"><item id=\"1\">value</item></root>"));

        assertThat(parser).isInstanceOf(MXParser.class);
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:test", "root");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:test", "item");
        assertThat(parser.getAttributeValue(null, "id")).isEqualTo("1");
        assertThat(parser.nextText()).isEqualTo("value");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, "urn:test", "root");

        XmlSerializer serializer = factory.newSerializer();
        assertThat(serializer).isInstanceOf(MXSerializer.class);
    }

    @Test
    void explicitParserAndSerializerNamesCreateBaseFactoryImplementations() throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance(
                MXParser.class.getName() + "," + MXSerializer.class.getName(), getClass());
        factory.setNamespaceAware(true);

        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader("<root><item>parsed</item></root>"));

        assertThat(parser).isInstanceOf(MXParser.class);
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, null, "root");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, null, "item");
        assertThat(parser.nextText()).isEqualTo("parsed");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, null, "root");

        XmlSerializer serializer = factory.newSerializer();
        StringWriter writer = new StringWriter();
        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", Boolean.TRUE);
        serializer.startTag(null, "root");
        serializer.attribute(null, "status", "ok");
        serializer.text("serialized");
        serializer.endTag(null, "root");
        serializer.endDocument();
        serializer.flush();

        assertThat(serializer).isInstanceOf(MXSerializer.class);
        assertThat(writer.toString()).contains("<root status=\"ok\">serialized</root>");
    }
}
