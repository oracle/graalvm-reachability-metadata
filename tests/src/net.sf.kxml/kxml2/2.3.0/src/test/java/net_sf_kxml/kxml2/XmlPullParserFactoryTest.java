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
import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

public class XmlPullParserFactoryTest {
    @Test
    void defaultFactoryResourceCreatesKxmlParserAndSerializer() throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);

        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader("<root xmlns=\"urn:kxml\"><item id=\"7\">value</item></root>"));

        assertThat(parser).isInstanceOf(KXmlParser.class);
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:kxml", "root");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:kxml", "item");
        assertThat(parser.getAttributeValue(null, "id")).isEqualTo("7");
        assertThat(parser.nextText()).isEqualTo("value");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, "urn:kxml", "root");

        XmlSerializer serializer = factory.newSerializer();
        StringWriter writer = new StringWriter();
        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", Boolean.TRUE);
        serializer.startTag(null, "answer");
        serializer.attribute(null, "result", "ok");
        serializer.text("42");
        serializer.endTag(null, "answer");
        serializer.endDocument();
        serializer.flush();

        assertThat(serializer).isInstanceOf(KXmlSerializer.class);
        assertThat(writer.toString()).contains("<answer result=\"ok\">42</answer>");
    }
}
