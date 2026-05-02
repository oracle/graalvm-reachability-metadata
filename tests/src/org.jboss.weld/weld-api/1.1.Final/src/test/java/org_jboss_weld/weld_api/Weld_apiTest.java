/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.ConversationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.SessionScoped;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.inject.Singleton;
import javax.interceptor.InvocationContext;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.jboss.weld.context.ApplicationContext;
import org.jboss.weld.context.BoundContext;
import org.jboss.weld.context.DependentContext;
import org.jboss.weld.context.ManagedContext;
import org.jboss.weld.context.ManagedConversation;
import org.jboss.weld.context.RequestContext;
import org.jboss.weld.context.SessionContext;
import org.jboss.weld.context.SingletonContext;
import org.jboss.weld.context.bound.Bound;
import org.jboss.weld.context.bound.BoundConversationContext;
import org.jboss.weld.context.bound.BoundLiteral;
import org.jboss.weld.context.bound.BoundRequest;
import org.jboss.weld.context.bound.BoundRequestContext;
import org.jboss.weld.context.bound.BoundSessionContext;
import org.jboss.weld.context.bound.MutableBoundRequest;
import org.jboss.weld.context.ejb.Ejb;
import org.jboss.weld.context.ejb.EjbLiteral;
import org.jboss.weld.context.ejb.EjbRequestContext;
import org.jboss.weld.context.http.Http;
import org.jboss.weld.context.http.HttpLiteral;
import org.jboss.weld.context.http.HttpRequestContext;
import org.jboss.weld.context.unbound.Unbound;
import org.jboss.weld.context.unbound.UnboundLiteral;
import org.junit.jupiter.api.Test;

public class Weld_apiTest {
    @Test
    void mutableBoundRequestExposesLiveRequestAndSessionMaps() {
        Map<String, Object> requestMap = new LinkedHashMap<>();
        Map<String, Object> sessionMap = new LinkedHashMap<>();
        requestMap.put("request-id", 42);
        sessionMap.put("user", "alice");

        MutableBoundRequest request = new MutableBoundRequest(requestMap, sessionMap);

        assertThat(request.getRequestMap()).isSameAs(requestMap).containsEntry("request-id", 42);
        assertThat(request.getSessionMap(false)).isSameAs(sessionMap).containsEntry("user", "alice");
        assertThat(request.getSessionMap(true)).isSameAs(sessionMap);

        request.getRequestMap().put("locale", "en-US");
        request.getSessionMap(false).put("role", "admin");

        assertThat(requestMap).containsEntry("locale", "en-US");
        assertThat(sessionMap).containsEntry("role", "admin");
    }

    @Test
    void mutableBoundRequestAllowsAbsentSessionStorage() {
        Map<String, Object> requestMap = new HashMap<>();
        MutableBoundRequest request = new MutableBoundRequest(requestMap, null);

        assertThat(request.getRequestMap()).isSameAs(requestMap);
        assertThat(request.getSessionMap(false)).isNull();
        assertThat(request.getSessionMap(true)).isNull();
    }

    @Test
    void qualifierLiteralsRepresentTheirAnnotationTypes() {
        assertLiteral(BoundLiteral.INSTANCE, Bound.class);
        assertLiteral(EjbLiteral.INSTANCE, Ejb.class);
        assertLiteral(HttpLiteral.INSTANCE, Http.class);
        assertLiteral(UnboundLiteral.INSTANCE, Unbound.class);
    }

    @Test
    void qualifierLiteralsCompareByAnnotationType() {
        Bound equivalentBound = new Bound() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Bound.class;
            }

            @Override
            public int hashCode() {
                return 0;
            }
        };
        Http equivalentHttp = new Http() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Http.class;
            }

            @Override
            public int hashCode() {
                return 0;
            }
        };

        assertThat(BoundLiteral.INSTANCE).isEqualTo(equivalentBound).hasSameHashCodeAs(equivalentBound);
        assertThat(HttpLiteral.INSTANCE).isEqualTo(equivalentHttp).hasSameHashCodeAs(equivalentHttp);
        assertThat(BoundLiteral.INSTANCE).isNotEqualTo(HttpLiteral.INSTANCE);
        assertThat(EjbLiteral.INSTANCE).isNotEqualTo(UnboundLiteral.INSTANCE);
    }

    @Test
    void managedContextLifecycleContractsCanBeImplementedAndUsedThroughApiTypes() {
        RequestContext requestContext = new RecordingManagedContext(RequestScoped.class);
        SessionContext sessionContext = new RecordingManagedContext(SessionScoped.class);

        exerciseManagedContext(requestContext);
        exerciseManagedContext(sessionContext);

        assertThat(requestContext.getScope()).isEqualTo(RequestScoped.class);
        assertThat(sessionContext.getScope()).isEqualTo(SessionScoped.class);
    }

    @Test
    void standardContextContractsExposeScopeAndActiveState() {
        RecordingContext applicationContext = new RecordingContext(ApplicationScoped.class);
        RecordingContext dependentContext = new RecordingContext(Dependent.class);
        RecordingContext singletonContext = new RecordingContext(Singleton.class);

        applicationContext.setActive(false);
        applicationContext.invalidate();

        assertThat(applicationContext.getScope()).isEqualTo(ApplicationScoped.class);
        assertThat(applicationContext.isActive()).isFalse();
        assertThat(applicationContext.invalidated).isTrue();
        assertThat(dependentContext.getScope()).isEqualTo(Dependent.class);
        assertThat(singletonContext.getScope()).isEqualTo(Singleton.class);
    }

    @Test
    void boundContextsAssociateAndDissociateStorageThroughPublicApi() {
        RecordingBoundContext requestContext = new RecordingBoundContext(RequestScoped.class);
        RecordingBoundContext sessionContext = new RecordingBoundContext(SessionScoped.class);
        Map<String, Object> storage = new HashMap<>();

        assertThat(associate(requestContext, storage)).isTrue();
        assertThat(requestContext.getAssociatedStorage()).isSameAs(storage);
        assertThat(dissociate(requestContext, storage)).isTrue();
        assertThat(requestContext.getAssociatedStorage()).isNull();

        assertThat(associate(sessionContext, storage)).isTrue();
        assertThat(sessionContext.getAssociatedStorage()).isSameAs(storage);
    }

    @Test
    void httpRequestContextBindsServletRequestStorage() {
        RecordingHttpRequestContext context = new RecordingHttpRequestContext();
        SimpleServletRequest request = new SimpleServletRequest();
        request.setParameter("name", "Ada", "Grace");
        request.setAttribute("request-token", "token-1");

        assertThat(context.associate(request)).isTrue();
        context.activate();

        assertThat(context.getScope()).isEqualTo(RequestScoped.class);
        assertThat(context.isActive()).isTrue();
        assertThat(context.getServletRequest()).isSameAs(request);
        assertThat(context.getServletRequest().getAttribute("request-token")).isEqualTo("token-1");
        assertThat(context.getServletRequest().getParameter("name")).isEqualTo("Ada");
        assertThat(context.getServletRequest().getParameterValues("name")).containsExactly("Ada", "Grace");
        assertThat(context.getServletRequest().getParameterMap()).containsKey("name");

        assertThat(context.dissociate(request)).isTrue();
        assertThat(context.getServletRequest()).isNull();
    }

    @Test
    void ejbRequestContextBindsInvocationContextForRequestProcessing() throws Exception {
        RecordingEjbRequestContext context = new RecordingEjbRequestContext();
        GreetingService service = new GreetingService();
        SimpleInvocationContext invocation = new SimpleInvocationContext(service, new Object[] {"Ada"});
        invocation.getContextData().put("request-token", "token-1");

        assertThat(context.associate(invocation)).isTrue();

        assertThat(context.getScope()).isEqualTo(RequestScoped.class);
        assertThat(context.getInvocationContext()).isSameAs(invocation);
        assertThat(context.getInvocationContext().getTarget()).isSameAs(service);
        assertThat(context.getInvocationContext().getContextData()).containsEntry("request-token", "token-1");
        assertThat(context.getInvocationContext().getParameters()).containsExactly("Ada");

        context.getInvocationContext().setParameters(new Object[] {"Grace"});

        assertThat(context.getInvocationContext().proceed()).isEqualTo("Hello Grace");
        assertThat(context.dissociate(invocation)).isTrue();
        assertThat(context.getInvocationContext()).isNull();
    }

    @Test
    void conversationContextManagesConversationSettingsAndLookup() {
        RecordingBoundConversationContext context = new RecordingBoundConversationContext();
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> sessionMap = new HashMap<>();
        MutableBoundRequest request = new MutableBoundRequest(requestMap, sessionMap);

        assertThat(context.associate(request)).isTrue();
        context.setParameterName("cid");
        context.setConcurrentAccessTimeout(1000L);
        context.setDefaultTimeout(2000L);
        context.activate("conversation-1");
        context.getCurrentConversation().begin("conversation-1");
        context.getCurrentConversation().setTimeout(2000L);
        context.getCurrentConversation().touch();

        assertThat(context.getParameterName()).isEqualTo("cid");
        assertThat(context.getConcurrentAccessTimeout()).isEqualTo(1000L);
        assertThat(context.getDefaultTimeout()).isEqualTo(2000L);
        assertThat(context.generateConversationId()).startsWith("conversation-");
        assertThat(context.getCurrentConversation().getId()).isEqualTo("conversation-1");
        assertThat(context.getCurrentConversation().getTimeout()).isEqualTo(2000L);
        assertThat(context.getCurrentConversation().getLastUsed()).isPositive();
        assertThat(context.getCurrentConversation().isTransient()).isFalse();
        assertThat(context.getCurrentConversation().lock(10L)).isTrue();
        assertThat(context.getCurrentConversation().unlock()).isTrue();
        context.getCurrentConversation().end();
        assertThat(context.getCurrentConversation().isTransient()).isTrue();
        assertThat(context.getConversation("conversation-1")).isSameAs(context.getCurrentConversation());
        assertThat(context.getConversations()).containsExactly(context.getCurrentConversation());
        assertThat(context.destroy(sessionMap)).isTrue();
        assertThat(context.dissociate(request)).isTrue();
    }

    private static void assertLiteral(Annotation literal, Class<? extends Annotation> annotationType) {
        assertThat(literal.annotationType()).isEqualTo(annotationType);
        assertThat(literal.toString()).contains(annotationType.getName());
    }

    private static void exerciseManagedContext(ManagedContext context) {
        assertThat(context.isActive()).isFalse();
        context.activate();
        assertThat(context.isActive()).isTrue();
        context.invalidate();
        context.deactivate();
        assertThat(context.isActive()).isFalse();
    }

    private static <S> boolean associate(BoundContext<S> context, S storage) {
        return context.associate(storage);
    }

    private static <S> boolean dissociate(BoundContext<S> context, S storage) {
        return context.dissociate(storage);
    }

    private static class RecordingContext implements ApplicationContext, DependentContext, SingletonContext {
        private final Class<? extends Annotation> scope;
        private boolean active = true;
        private boolean invalidated;

        RecordingContext(Class<? extends Annotation> scope) {
            this.scope = scope;
        }

        void setActive(boolean active) {
            this.active = active;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return scope;
        }

        @Override
        public <U> U get(Contextual<U> contextual, CreationalContext<U> creationalContext) {
            if (!active) {
                throw new ContextNotActiveException();
            }
            return contextual.create(creationalContext);
        }

        @Override
        public <U> U get(Contextual<U> contextual) {
            return null;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void invalidate() {
            invalidated = true;
        }
    }

    private static class RecordingManagedContext implements RequestContext, SessionContext {
        private final Class<? extends Annotation> scope;
        private boolean active;
        private boolean invalidated;

        RecordingManagedContext(Class<? extends Annotation> scope) {
            this.scope = scope;
        }

        @Override
        public void activate() {
            active = true;
        }

        @Override
        public void deactivate() {
            active = false;
        }

        @Override
        public void invalidate() {
            invalidated = true;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return scope;
        }

        @Override
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            return contextual.create(creationalContext);
        }

        @Override
        public <T> T get(Contextual<T> contextual) {
            return null;
        }

        @Override
        public boolean isActive() {
            return active && !invalidated;
        }
    }

    private static class RecordingBoundContext extends RecordingManagedContext
            implements BoundRequestContext, BoundSessionContext {
        private Map<String, Object> associatedStorage;

        RecordingBoundContext(Class<? extends Annotation> scope) {
            super(scope);
        }

        @Override
        public boolean associate(Map<String, Object> storage) {
            associatedStorage = storage;
            return true;
        }

        @Override
        public boolean dissociate(Map<String, Object> storage) {
            if (associatedStorage == storage) {
                associatedStorage = null;
                return true;
            }
            return false;
        }

        Map<String, Object> getAssociatedStorage() {
            return associatedStorage;
        }
    }

    private static final class RecordingHttpRequestContext extends RecordingManagedContext implements HttpRequestContext {
        private ServletRequest servletRequest;

        RecordingHttpRequestContext() {
            super(RequestScoped.class);
        }

        @Override
        public boolean associate(ServletRequest storage) {
            servletRequest = storage;
            return true;
        }

        @Override
        public boolean dissociate(ServletRequest storage) {
            if (servletRequest == storage) {
                servletRequest = null;
                return true;
            }
            return false;
        }

        ServletRequest getServletRequest() {
            return servletRequest;
        }
    }

    @SuppressWarnings("deprecation")
    private static final class SimpleServletRequest implements ServletRequest {
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private final Map<String, String[]> parameters = new LinkedHashMap<>();
        private String characterEncoding = "UTF-8";

        void setParameter(String name, String... values) {
            parameters.put(name, values.clone());
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        @Override
        public String getCharacterEncoding() {
            return characterEncoding;
        }

        @Override
        public void setCharacterEncoding(String encoding) throws UnsupportedEncodingException {
            characterEncoding = encoding;
        }

        @Override
        public int getContentLength() {
            return 0;
        }

        @Override
        public long getContentLengthLong() {
            return 0L;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return null;
        }

        @Override
        public String getParameter(String name) {
            String[] values = parameters.get(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = parameters.get(name);
            return values == null ? null : values.clone();
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Collections.unmodifiableMap(parameters);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public String getScheme() {
            return "http";
        }

        @Override
        public String getServerName() {
            return "localhost";
        }

        @Override
        public int getServerPort() {
            return 80;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return null;
        }

        @Override
        public String getRemoteAddr() {
            return "127.0.0.1";
        }

        @Override
        public String getRemoteHost() {
            return "localhost";
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return Collections.enumeration(Collections.singletonList(Locale.ROOT));
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            return null;
        }

        @Override
        public String getRealPath(String path) {
            return path;
        }

        @Override
        public int getRemotePort() {
            return 0;
        }

        @Override
        public String getLocalName() {
            return "localhost";
        }

        @Override
        public String getLocalAddr() {
            return "127.0.0.1";
        }

        @Override
        public int getLocalPort() {
            return 80;
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public AsyncContext startAsync() throws IllegalStateException {
            return null;
        }

        @Override
        public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse)
                throws IllegalStateException {
            return null;
        }

        @Override
        public boolean isAsyncStarted() {
            return false;
        }

        @Override
        public boolean isAsyncSupported() {
            return false;
        }

        @Override
        public AsyncContext getAsyncContext() {
            return null;
        }

        @Override
        public DispatcherType getDispatcherType() {
            return DispatcherType.REQUEST;
        }
    }

    private static final class RecordingEjbRequestContext extends RecordingManagedContext implements EjbRequestContext {
        private InvocationContext invocationContext;

        RecordingEjbRequestContext() {
            super(RequestScoped.class);
        }

        @Override
        public boolean associate(InvocationContext storage) {
            invocationContext = storage;
            return true;
        }

        @Override
        public boolean dissociate(InvocationContext storage) {
            if (invocationContext == storage) {
                invocationContext = null;
                return true;
            }
            return false;
        }

        InvocationContext getInvocationContext() {
            return invocationContext;
        }
    }

    private static final class SimpleInvocationContext implements InvocationContext {
        private final GreetingService target;
        private final Map<String, Object> contextData = new LinkedHashMap<>();
        private Object[] parameters;

        SimpleInvocationContext(GreetingService target, Object[] parameters) {
            this.target = target;
            setParameters(parameters);
        }

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public Method getMethod() {
            return null;
        }

        @Override
        public Object[] getParameters() {
            return parameters.clone();
        }

        @Override
        public void setParameters(Object[] parameters) {
            this.parameters = parameters.clone();
        }

        @Override
        public Map<String, Object> getContextData() {
            return contextData;
        }

        @Override
        public Object getTimer() {
            return null;
        }

        @Override
        public Object proceed() {
            return target.greet((String) parameters[0]);
        }
    }

    private static final class GreetingService {
        String greet(String name) {
            return "Hello " + name;
        }
    }

    private static final class RecordingBoundConversationContext extends RecordingManagedContext
            implements BoundConversationContext {
        private BoundRequest associatedRequest;
        private String parameterName = "cid";
        private long concurrentAccessTimeout;
        private long defaultTimeout;
        private RecordingManagedConversation currentConversation = new RecordingManagedConversation("transient");

        RecordingBoundConversationContext() {
            super(ConversationScoped.class);
        }

        @Override
        public boolean associate(BoundRequest storage) {
            associatedRequest = storage;
            return true;
        }

        @Override
        public boolean dissociate(BoundRequest storage) {
            if (associatedRequest == storage) {
                associatedRequest = null;
                return true;
            }
            return false;
        }

        @Override
        public boolean destroy(Map<String, Object> session) {
            session.clear();
            return true;
        }

        @Override
        public void activate(String cid) {
            activate();
            currentConversation = new RecordingManagedConversation(cid);
        }

        @Override
        public void setParameterName(String parameterName) {
            this.parameterName = parameterName;
        }

        @Override
        public String getParameterName() {
            return parameterName;
        }

        @Override
        public void setConcurrentAccessTimeout(long timeout) {
            concurrentAccessTimeout = timeout;
        }

        @Override
        public long getConcurrentAccessTimeout() {
            return concurrentAccessTimeout;
        }

        @Override
        public void setDefaultTimeout(long timeout) {
            defaultTimeout = timeout;
        }

        @Override
        public long getDefaultTimeout() {
            return defaultTimeout;
        }

        @Override
        public Collection<ManagedConversation> getConversations() {
            return Collections.<ManagedConversation>singletonList(currentConversation);
        }

        @Override
        public ManagedConversation getConversation(String id) {
            if (currentConversation.getId().equals(id)) {
                return currentConversation;
            }
            return null;
        }

        @Override
        public String generateConversationId() {
            return "conversation-" + System.identityHashCode(this);
        }

        @Override
        public ManagedConversation getCurrentConversation() {
            return currentConversation;
        }
    }

    private static final class RecordingManagedConversation implements ManagedConversation {
        private final String id;
        private boolean transientConversation = true;
        private long timeout;
        private long lastUsed;
        private boolean locked;

        RecordingManagedConversation(String id) {
            this.id = id;
            touch();
        }

        @Override
        public void begin() {
            transientConversation = false;
        }

        @Override
        public void begin(String id) {
            assertThat(id).isEqualTo(this.id);
            begin();
        }

        @Override
        public void end() {
            transientConversation = true;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public long getTimeout() {
            return timeout;
        }

        @Override
        public void setTimeout(long milliseconds) {
            timeout = milliseconds;
        }

        @Override
        public boolean isTransient() {
            return transientConversation;
        }

        @Override
        public boolean unlock() {
            boolean wasLocked = locked;
            locked = false;
            return wasLocked;
        }

        @Override
        public boolean lock(long timeout) {
            locked = true;
            return true;
        }

        @Override
        public long getLastUsed() {
            return lastUsed;
        }

        @Override
        public void touch() {
            lastUsed = System.currentTimeMillis();
        }
    }
}
