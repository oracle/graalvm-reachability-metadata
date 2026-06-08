/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.view.xslt.XsltView;

import static org.assertj.core.api.Assertions.assertThat;

public class XsltViewTest {

    @Test
    void rendersWithConfiguredTransformerFactoryClass() throws Exception {
        RecordingTransformerFactory.reset();
        GenericWebApplicationContext applicationContext = new GenericWebApplicationContext();
        applicationContext.setServletContext(new MockServletContext());
        applicationContext.refresh();

        try {
            XsltView view = new StaticStylesheetXsltView();
            view.setUrl("test-stylesheet");
            view.setSourceKey("xml");
            view.setTransformerFactoryClass(RecordingTransformerFactory.class);
            view.setApplicationContext(applicationContext);
            view.afterPropertiesSet();

            MockHttpServletResponse response = new MockHttpServletResponse();
            view.render(Map.of(
                    "xml", new StringReader("<message name=\"Spring\"/>"),
                    "greeting", "Hello"), new MockHttpServletRequest(), response);

            assertThat(RecordingTransformerFactory.instances).hasValue(1);
            assertThat(response.getContentAsString()).isEqualTo("Hello Spring");
        }
        finally {
            applicationContext.close();
        }
    }

    public static class StaticStylesheetXsltView extends XsltView {
        private static final String STYLESHEET = String.join("",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">",
                "<xsl:output method=\"text\" encoding=\"UTF-8\" media-type=\"text/plain\"/>",
                "<xsl:param name=\"greeting\"/>",
                "<xsl:template match=\"/\"><xsl:value-of select=\"$greeting\"/><xsl:text> </xsl:text>",
                "<xsl:value-of select=\"/message/@name\"/></xsl:template>",
                "</xsl:stylesheet>");

        @Override
        protected Source getStylesheetSource() {
            return new StreamSource(new StringReader(STYLESHEET), "test-stylesheet");
        }
    }

    public static class RecordingTransformerFactory extends TransformerFactory {
        static final AtomicInteger instances = new AtomicInteger();

        private final TransformerFactory delegate;

        public RecordingTransformerFactory() {
            instances.incrementAndGet();
            this.delegate = TransformerFactory.newInstance();
        }

        static void reset() {
            instances.set(0);
        }

        @Override
        public Transformer newTransformer(Source source) throws TransformerConfigurationException {
            return this.delegate.newTransformer(source);
        }

        @Override
        public Transformer newTransformer() throws TransformerConfigurationException {
            return this.delegate.newTransformer();
        }

        @Override
        public Templates newTemplates(Source source) throws TransformerConfigurationException {
            return this.delegate.newTemplates(source);
        }

        @Override
        public Source getAssociatedStylesheet(Source source, String media, String title, String charset)
                throws TransformerConfigurationException {
            return this.delegate.getAssociatedStylesheet(source, media, title, charset);
        }

        @Override
        public void setURIResolver(URIResolver resolver) {
            this.delegate.setURIResolver(resolver);
        }

        @Override
        public URIResolver getURIResolver() {
            return this.delegate.getURIResolver();
        }

        @Override
        public void setFeature(String name, boolean value) throws TransformerConfigurationException {
            this.delegate.setFeature(name, value);
        }

        @Override
        public boolean getFeature(String name) {
            return this.delegate.getFeature(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            this.delegate.setAttribute(name, value);
        }

        @Override
        public Object getAttribute(String name) {
            return this.delegate.getAttribute(name);
        }

        @Override
        public void setErrorListener(ErrorListener listener) {
            this.delegate.setErrorListener(listener);
        }

        @Override
        public ErrorListener getErrorListener() {
            return this.delegate.getErrorListener();
        }
    }
}
