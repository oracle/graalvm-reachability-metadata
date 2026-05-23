/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.view.xslt.XsltView;

import static org.assertj.core.api.Assertions.assertThat;

public class XsltViewTest {
    private static final String STYLESHEET = """
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:output method="text" media-type="text/plain" encoding="UTF-8"/>
                <xsl:template match="/message"><xsl:value-of select="."/></xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void renderUsesConfiguredTransformerFactoryClass() throws Exception {
        RecordingTransformerFactory.reset();
        GenericWebApplicationContext applicationContext = new GenericWebApplicationContext();
        applicationContext.setServletContext(new MockServletContext());
        applicationContext.refresh();

        InlineStylesheetXsltView view = new InlineStylesheetXsltView();
        view.setUrl("inline-message-stylesheet.xsl");
        view.setSourceKey("xml");
        view.setCacheTemplates(false);
        view.setTransformerFactoryClass(RecordingTransformerFactory.class);

        try {
            view.afterPropertiesSet();
            view.setApplicationContext(applicationContext);

            MockHttpServletResponse response = new MockHttpServletResponse();
            Map<String, Object> model = Map.of("xml", new StreamSource(new StringReader("<message>Hello</message>")));

            view.render(model, new MockHttpServletRequest(), response);

            assertThat(RecordingTransformerFactory.constructorCalls()).isEqualTo(1);
            assertThat(response.getContentAsString()).isEqualTo("Hello");
            assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
        } finally {
            applicationContext.close();
        }
    }

    public static class InlineStylesheetXsltView extends XsltView {
        @Override
        protected Source getStylesheetSource() {
            return new StreamSource(new StringReader(STYLESHEET), "inline-message-stylesheet.xsl");
        }
    }

    public static class RecordingTransformerFactory extends TransformerFactory {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        private final Map<String, Object> attributes = new HashMap<>();
        private final Map<String, Boolean> features = new HashMap<>();
        private URIResolver uriResolver;
        private ErrorListener errorListener;

        public RecordingTransformerFactory() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        static void reset() {
            CONSTRUCTOR_CALLS.set(0);
        }

        static int constructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }

        @Override
        public Transformer newTransformer(Source source) {
            return new MessageTransformer();
        }

        @Override
        public Transformer newTransformer() {
            return new MessageTransformer();
        }

        @Override
        public Templates newTemplates(Source source) {
            return new MessageTemplates();
        }

        @Override
        public Source getAssociatedStylesheet(Source source, String media, String title, String charset) {
            return null;
        }

        @Override
        public void setURIResolver(URIResolver resolver) {
            this.uriResolver = resolver;
        }

        @Override
        public URIResolver getURIResolver() {
            return this.uriResolver;
        }

        @Override
        public void setFeature(String name, boolean value) {
            this.features.put(name, value);
        }

        @Override
        public boolean getFeature(String name) {
            return this.features.getOrDefault(name, false);
        }

        @Override
        public void setAttribute(String name, Object value) {
            this.attributes.put(name, value);
        }

        @Override
        public Object getAttribute(String name) {
            return this.attributes.get(name);
        }

        @Override
        public void setErrorListener(ErrorListener listener) {
            this.errorListener = listener;
        }

        @Override
        public ErrorListener getErrorListener() {
            return this.errorListener;
        }
    }

    public static class MessageTemplates implements Templates {
        @Override
        public Transformer newTransformer() {
            return new MessageTransformer();
        }

        @Override
        public Properties getOutputProperties() {
            return MessageTransformer.defaultOutputProperties();
        }
    }

    public static class MessageTransformer extends Transformer {
        private final Properties outputProperties = defaultOutputProperties();
        private final Map<String, Object> parameters = new HashMap<>();
        private URIResolver uriResolver;
        private ErrorListener errorListener;

        static Properties defaultOutputProperties() {
            Properties properties = new Properties();
            properties.setProperty(OutputKeys.MEDIA_TYPE, "text/plain");
            properties.setProperty(OutputKeys.ENCODING, "UTF-8");
            return properties;
        }

        @Override
        public void transform(Source xmlSource, Result outputTarget) throws TransformerException {
            String xml = readSource(xmlSource);
            writeResult(outputTarget, extractMessage(xml));
        }

        @Override
        public void setParameter(String name, Object value) {
            this.parameters.put(name, value);
        }

        @Override
        public Object getParameter(String name) {
            return this.parameters.get(name);
        }

        @Override
        public void clearParameters() {
            this.parameters.clear();
        }

        @Override
        public void setURIResolver(URIResolver resolver) {
            this.uriResolver = resolver;
        }

        @Override
        public URIResolver getURIResolver() {
            return this.uriResolver;
        }

        @Override
        public void setOutputProperties(Properties oformat) {
            this.outputProperties.clear();
            if (oformat != null) {
                this.outputProperties.putAll(oformat);
            }
        }

        @Override
        public Properties getOutputProperties() {
            Properties copy = new Properties();
            copy.putAll(this.outputProperties);
            return copy;
        }

        @Override
        public void setOutputProperty(String name, String value) {
            this.outputProperties.setProperty(name, value);
        }

        @Override
        public String getOutputProperty(String name) {
            return this.outputProperties.getProperty(name);
        }

        @Override
        public void setErrorListener(ErrorListener listener) {
            this.errorListener = listener;
        }

        @Override
        public ErrorListener getErrorListener() {
            return this.errorListener;
        }

        private static String extractMessage(String xml) {
            int start = xml.indexOf("<message>");
            int end = xml.indexOf("</message>");
            if (start == -1 || end == -1 || end < start) {
                return xml;
            }
            return xml.substring(start + "<message>".length(), end);
        }

        private static String readSource(Source source) throws TransformerException {
            if (!(source instanceof StreamSource)) {
                throw new TransformerException("Only StreamSource is supported by this test transformer");
            }
            StreamSource streamSource = (StreamSource) source;
            try {
                if (streamSource.getReader() != null) {
                    return readReader(streamSource.getReader());
                }
                if (streamSource.getInputStream() != null) {
                    return readInputStream(streamSource.getInputStream());
                }
                return "";
            } catch (IOException ex) {
                throw new TransformerException(ex);
            }
        }

        private static String readReader(Reader reader) throws IOException {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[256];
            int length;
            while ((length = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, length);
            }
            return builder.toString();
        }

        private static String readInputStream(InputStream inputStream) throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return outputStream.toString(StandardCharsets.UTF_8);
        }

        private static void writeResult(Result result, String value) throws TransformerException {
            if (!(result instanceof StreamResult)) {
                throw new TransformerException("Only StreamResult is supported by this test transformer");
            }
            StreamResult streamResult = (StreamResult) result;
            try {
                Writer writer = streamResult.getWriter();
                if (writer != null) {
                    writer.write(value);
                    writer.flush();
                    return;
                }
                OutputStream outputStream = streamResult.getOutputStream();
                if (outputStream != null) {
                    outputStream.write(value.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    return;
                }
            } catch (IOException ex) {
                throw new TransformerException(ex);
            }
            throw new TransformerException("StreamResult has no writer or output stream");
        }
    }
}
