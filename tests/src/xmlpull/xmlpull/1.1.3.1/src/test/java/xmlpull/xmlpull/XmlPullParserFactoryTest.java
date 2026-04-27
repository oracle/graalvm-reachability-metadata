/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xmlpull.xmlpull;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

public class XmlPullParserFactoryTest {
    @Test
    void newInstanceWithExplicitImplementationsCreatesParserAndSerializer() throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance(
                RecordingParser.class.getName() + "," + RecordingSerializer.class.getName(), getClass());
        factory.setNamespaceAware(true);
        factory.setValidating(false);

        XmlPullParser parser = factory.newPullParser();
        assertTrue(parser instanceof RecordingParser);
        assertTrue(parser.getFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES));
        assertFalse(parser.getFeature(XmlPullParser.FEATURE_VALIDATION));

        XmlSerializer serializer = factory.newSerializer();
        assertTrue(serializer instanceof RecordingSerializer);
    }

    @Test
    void defaultNewInstanceLoadsImplementationsFromServiceResource() throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();

        assertTrue(factory.newPullParser() instanceof RecordingParser);
        assertTrue(factory.newSerializer() instanceof RecordingSerializer);
    }

    public static final class RecordingParser implements XmlPullParser {
        private final Map<String, Boolean> features = new HashMap<>();

        public RecordingParser() {
        }

        @Override
        public void setFeature(String name, boolean state) {
            features.put(name, state);
        }

        @Override
        public boolean getFeature(String name) {
            return features.getOrDefault(name, false);
        }

        @Override
        public void setProperty(String name, Object value) throws XmlPullParserException {
            throw new XmlPullParserException("Properties are not supported by this test parser");
        }

        @Override
        public Object getProperty(String name) {
            return null;
        }

        @Override
        public void setInput(Reader in) {
        }

        @Override
        public void setInput(InputStream inputStream, String inputEncoding) {
        }

        @Override
        public String getInputEncoding() {
            return null;
        }

        @Override
        public void defineEntityReplacementText(String entityName, String replacementText) {
        }

        @Override
        public int getNamespaceCount(int depth) {
            return 0;
        }

        @Override
        public String getNamespacePrefix(int pos) {
            return null;
        }

        @Override
        public String getNamespaceUri(int pos) {
            return null;
        }

        @Override
        public String getNamespace(String prefix) {
            return null;
        }

        @Override
        public int getDepth() {
            return 0;
        }

        @Override
        public String getPositionDescription() {
            return "test parser";
        }

        @Override
        public int getLineNumber() {
            return 0;
        }

        @Override
        public int getColumnNumber() {
            return 0;
        }

        @Override
        public boolean isWhitespace() {
            return false;
        }

        @Override
        public String getText() {
            return null;
        }

        @Override
        public char[] getTextCharacters(int[] holderForStartAndLength) {
            holderForStartAndLength[0] = 0;
            holderForStartAndLength[1] = 0;
            return new char[0];
        }

        @Override
        public String getNamespace() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getPrefix() {
            return null;
        }

        @Override
        public boolean isEmptyElementTag() {
            return false;
        }

        @Override
        public int getAttributeCount() {
            return 0;
        }

        @Override
        public String getAttributeNamespace(int index) {
            return null;
        }

        @Override
        public String getAttributeName(int index) {
            return null;
        }

        @Override
        public String getAttributePrefix(int index) {
            return null;
        }

        @Override
        public String getAttributeType(int index) {
            return null;
        }

        @Override
        public boolean isAttributeDefault(int index) {
            return false;
        }

        @Override
        public String getAttributeValue(int index) {
            return null;
        }

        @Override
        public String getAttributeValue(String namespace, String name) {
            return null;
        }

        @Override
        public int getEventType() {
            return START_DOCUMENT;
        }

        @Override
        public int next() {
            return END_DOCUMENT;
        }

        @Override
        public int nextToken() {
            return END_DOCUMENT;
        }

        @Override
        public void require(int type, String namespace, String name) {
        }

        @Override
        public String nextText() {
            return "";
        }

        @Override
        public int nextTag() {
            return END_TAG;
        }
    }

    public static final class RecordingSerializer implements XmlSerializer {
        public RecordingSerializer() {
        }

        @Override
        public void setFeature(String name, boolean state) {
        }

        @Override
        public boolean getFeature(String name) {
            return false;
        }

        @Override
        public void setProperty(String name, Object value) {
        }

        @Override
        public Object getProperty(String name) {
            return null;
        }

        @Override
        public void setOutput(OutputStream os, String encoding) throws IOException {
        }

        @Override
        public void setOutput(Writer writer) throws IOException {
        }

        @Override
        public void startDocument(String encoding, Boolean standalone) throws IOException {
        }

        @Override
        public void endDocument() throws IOException {
        }

        @Override
        public void setPrefix(String prefix, String namespace) throws IOException {
        }

        @Override
        public String getPrefix(String namespace, boolean generatePrefix) {
            return null;
        }

        @Override
        public int getDepth() {
            return 0;
        }

        @Override
        public String getNamespace() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public XmlSerializer startTag(String namespace, String name) throws IOException {
            return this;
        }

        @Override
        public XmlSerializer attribute(String namespace, String name, String value) throws IOException {
            return this;
        }

        @Override
        public XmlSerializer endTag(String namespace, String name) throws IOException {
            return this;
        }

        @Override
        public XmlSerializer text(String text) throws IOException {
            return this;
        }

        @Override
        public XmlSerializer text(char[] buf, int start, int len) throws IOException {
            return this;
        }

        @Override
        public void cdsect(String text) throws IOException {
        }

        @Override
        public void entityRef(String text) throws IOException {
        }

        @Override
        public void processingInstruction(String text) throws IOException {
        }

        @Override
        public void comment(String text) throws IOException {
        }

        @Override
        public void docdecl(String text) throws IOException {
        }

        @Override
        public void ignorableWhitespace(String text) throws IOException {
        }

        @Override
        public void flush() throws IOException {
        }
    }
}
