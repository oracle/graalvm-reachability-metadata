/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.view.xslt.XsltView;

public class XsltViewTest {
    @Test
    void applicationContextInitializationCreatesConfiguredTransformerFactory() throws Exception {
        RecordingTransformerFactory.reset();

        StaticWebApplicationContext applicationContext = new StaticWebApplicationContext();
        applicationContext.setServletContext(new MockServletContext());
        applicationContext.refresh();

        XsltView view = new XsltView();
        view.setUrl("unused.xsl");
        view.setCacheTemplates(false);
        view.setTransformerFactoryClass(RecordingTransformerFactory.class);

        try {
            view.setApplicationContext(applicationContext);

            assertThat(RecordingTransformerFactory.constructedCount).isEqualTo(1);
            assertThat(RecordingTransformerFactory.errorListenerConfigured).isTrue();
        } finally {
            applicationContext.close();
            RecordingTransformerFactory.reset();
        }
    }
}

final class RecordingTransformerFactory extends TransformerFactory {
    static int constructedCount;
    static boolean errorListenerConfigured;

    private URIResolver uriResolver;
    private ErrorListener errorListener;

    RecordingTransformerFactory() {
        constructedCount++;
    }

    static void reset() {
        constructedCount = 0;
        errorListenerConfigured = false;
    }

    @Override
    public Transformer newTransformer(Source source) throws TransformerConfigurationException {
        throw new TransformerConfigurationException("This test factory does not transform sources");
    }

    @Override
    public Transformer newTransformer() throws TransformerConfigurationException {
        throw new TransformerConfigurationException("This test factory does not transform sources");
    }

    @Override
    public Templates newTemplates(Source source) throws TransformerConfigurationException {
        throw new TransformerConfigurationException("This test factory does not compile templates");
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
    public void setFeature(String name, boolean value) throws TransformerConfigurationException {
        if (value) {
            throw new TransformerConfigurationException("Unsupported feature: " + name);
        }
    }

    @Override
    public boolean getFeature(String name) {
        return false;
    }

    @Override
    public void setAttribute(String name, Object value) {
        throw new IllegalArgumentException("Unsupported attribute: " + name);
    }

    @Override
    public Object getAttribute(String name) {
        throw new IllegalArgumentException("Unsupported attribute: " + name);
    }

    @Override
    public void setErrorListener(ErrorListener listener) {
        this.errorListener = listener;
        errorListenerConfigured = listener != null;
    }

    @Override
    public ErrorListener getErrorListener() {
        return this.errorListener;
    }
}
