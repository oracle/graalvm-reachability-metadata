/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.el.ELContext;
import jakarta.faces.application.Application;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.context.ResponseStream;
import jakarta.faces.context.ResponseWriter;
import jakarta.faces.lifecycle.Lifecycle;
import jakarta.faces.render.RenderKit;

final class TestFacesContext extends FacesContext implements AutoCloseable {
    private final FacesContext previous;
    private final TestApplication application = new TestApplication();
    private final TestExternalContext externalContext = new TestExternalContext();
    private final Map<Object, Object> attributes = new HashMap<>();
    private final ELContext elContext = new TestELContext();
    private final Map<String, List<FacesMessage>> messages = new LinkedHashMap<>();
    private UIViewRoot viewRoot;
    private ResponseStream responseStream;
    private ResponseWriter responseWriter;
    private boolean renderResponse;
    private boolean responseComplete;
    private boolean released;

    TestFacesContext() {
        previous = FacesContext.getCurrentInstance();
        setCurrentInstance(this);
        viewRoot = new UIViewRoot();
        viewRoot.setLocale(Locale.US);
    }

    @Override
    public ELContext getELContext() {
        return elContext;
    }

    @Override
    public Application getApplication() {
        return application;
    }

    @Override
    public Map<Object, Object> getAttributes() {
        return attributes;
    }

    @Override
    public ExternalContext getExternalContext() {
        return externalContext;
    }

    @Override
    public UIViewRoot getViewRoot() {
        return viewRoot;
    }

    @Override
    public void setViewRoot(UIViewRoot viewRoot) {
        this.viewRoot = viewRoot;
    }

    @Override
    public void addMessage(String clientId, FacesMessage message) {
        messages.computeIfAbsent(clientId, key -> new ArrayList<>()).add(message);
    }

    @Override
    public Iterator<String> getClientIdsWithMessages() {
        return messages.keySet().iterator();
    }

    @Override
    public Iterator<FacesMessage> getMessages() {
        return messages.values().stream().flatMap(List::stream).iterator();
    }

    @Override
    public Iterator<FacesMessage> getMessages(String clientId) {
        return messages.getOrDefault(clientId, List.of()).iterator();
    }

    @Override
    public FacesMessage.Severity getMaximumSeverity() {
        return messages.values().stream().flatMap(List::stream).map(FacesMessage::getSeverity)
                .max((left, right) -> left.compareTo(right)).orElse(null);
    }

    @Override
    public Lifecycle getLifecycle() {
        throw new UnsupportedOperationException("No request lifecycle in component unit tests");
    }

    @Override
    public RenderKit getRenderKit() {
        return null;
    }

    @Override
    public ResponseStream getResponseStream() {
        return responseStream;
    }

    @Override
    public void setResponseStream(ResponseStream responseStream) {
        this.responseStream = responseStream;
    }

    @Override
    public ResponseWriter getResponseWriter() {
        return responseWriter;
    }

    @Override
    public void setResponseWriter(ResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public boolean getRenderResponse() {
        return renderResponse;
    }

    @Override
    public void renderResponse() {
        renderResponse = true;
    }

    @Override
    public boolean getResponseComplete() {
        return responseComplete;
    }

    @Override
    public void responseComplete() {
        responseComplete = true;
    }

    @Override
    public boolean isReleased() {
        return released;
    }

    @Override
    public void release() {
        setCurrentInstance(previous);
        released = true;
    }

    @Override
    public void close() {
        release();
    }
}
