/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.httpspi_servlet;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.xml.transform.Source;

import jakarta.xml.ws.Binding;
import jakarta.xml.ws.Endpoint;
import jakarta.xml.ws.EndpointReference;
import jakarta.xml.ws.spi.http.HttpContext;
import org.w3c.dom.Element;

public final class RecordingEndpoint extends Endpoint {
    HttpContext publishedContext;
    private boolean stopped;

    @Override
    public void publish(final HttpContext context) {
        publishedContext = context;
    }

    @Override
    public void stop() {
        stopped = true;
    }

    @Override
    public boolean isPublished() {
        return publishedContext != null && !stopped;
    }

    @Override
    public Binding getBinding() {
        throw unsupported();
    }

    @Override
    public Object getImplementor() {
        throw unsupported();
    }

    @Override
    public void publish(final String address) {
        throw unsupported();
    }

    @Override
    public void publish(final Object serverContext) {
        throw unsupported();
    }

    @Override
    public List<Source> getMetadata() {
        throw unsupported();
    }

    @Override
    public void setMetadata(final List<Source> metadata) {
        throw unsupported();
    }

    @Override
    public Executor getExecutor() {
        throw unsupported();
    }

    @Override
    public void setExecutor(final Executor executor) {
        throw unsupported();
    }

    @Override
    public Map<String, Object> getProperties() {
        throw unsupported();
    }

    @Override
    public void setProperties(final Map<String, Object> properties) {
        throw unsupported();
    }

    @Override
    public EndpointReference getEndpointReference(final Element... referenceParameters) {
        throw unsupported();
    }

    @Override
    public <T extends EndpointReference> T getEndpointReference(
            final Class<T> clazz, final Element... referenceParameters) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Not used by this endpoint adapter test");
    }
}
