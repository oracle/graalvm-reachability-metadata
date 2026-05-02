/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_portlet.portlet_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.BaseURL;
import javax.portlet.CacheControl;
import javax.portlet.Event;
import javax.portlet.EventRequest;
import javax.portlet.EventResponse;
import javax.portlet.GenericPortlet;
import javax.portlet.MimeResponse;
import javax.portlet.PortalContext;
import javax.portlet.PortletConfig;
import javax.portlet.PortletContext;
import javax.portlet.PortletException;
import javax.portlet.PortletMode;
import javax.portlet.PortletModeException;
import javax.portlet.PortletPreferences;
import javax.portlet.PortletRequest;
import javax.portlet.PortletRequestDispatcher;
import javax.portlet.PortletResponse;
import javax.portlet.PortletSecurityException;
import javax.portlet.PortletSession;
import javax.portlet.PortletSessionUtil;
import javax.portlet.PortletURL;
import javax.portlet.PreferencesValidator;
import javax.portlet.ProcessAction;
import javax.portlet.ProcessEvent;
import javax.portlet.ReadOnlyException;
import javax.portlet.RenderMode;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.portlet.ResourceURL;
import javax.portlet.UnavailableException;
import javax.portlet.ValidatorException;
import javax.portlet.WindowState;
import javax.portlet.WindowStateException;
import javax.portlet.filter.ActionFilter;
import javax.portlet.filter.ActionRequestWrapper;
import javax.portlet.filter.ActionResponseWrapper;
import javax.portlet.filter.EventFilter;
import javax.portlet.filter.EventRequestWrapper;
import javax.portlet.filter.EventResponseWrapper;
import javax.portlet.filter.FilterChain;
import javax.portlet.filter.FilterConfig;
import javax.portlet.filter.PortletRequestWrapper;
import javax.portlet.filter.RenderFilter;
import javax.portlet.filter.RenderRequestWrapper;
import javax.portlet.filter.RenderResponseWrapper;
import javax.portlet.filter.ResourceFilter;
import javax.portlet.filter.ResourceRequestWrapper;
import javax.portlet.filter.ResourceResponseWrapper;
import javax.xml.namespace.QName;

import org.junit.jupiter.api.Test;

public class Portlet_apiTest {
    private static final String DEFAULT_NAMESPACE = "urn:test";

    @Test
    void valueObjectsNormalizeNamesAndRejectNulls() {
        PortletMode mixedCaseView = new PortletMode("VIEW");
        PortletMode customMode = new PortletMode("CONFIGURE");
        WindowState mixedCaseMaximized = new WindowState("MAXIMIZED");
        WindowState customState = new WindowState("DETACHED");

        assertThat(mixedCaseView).isEqualTo(PortletMode.VIEW);
        assertThat(mixedCaseView.hashCode()).isEqualTo(PortletMode.VIEW.hashCode());
        assertThat(PortletMode.VIEW.toString()).isEqualTo("view");
        assertThat(PortletMode.EDIT.toString()).isEqualTo("edit");
        assertThat(PortletMode.HELP.toString()).isEqualTo("help");
        assertThat(customMode).isEqualTo(new PortletMode("configure"));
        assertThat(customMode).isNotEqualTo("configure");
        assertThat(customMode.toString()).isEqualTo("configure");

        assertThat(mixedCaseMaximized).isEqualTo(WindowState.MAXIMIZED);
        assertThat(mixedCaseMaximized.hashCode()).isEqualTo(WindowState.MAXIMIZED.hashCode());
        assertThat(WindowState.NORMAL.toString()).isEqualTo("normal");
        assertThat(WindowState.MAXIMIZED.toString()).isEqualTo("maximized");
        assertThat(WindowState.MINIMIZED.toString()).isEqualTo("minimized");
        assertThat(customState).isEqualTo(new WindowState("detached"));
        assertThat(customState).isNotEqualTo("detached");
        assertThat(customState.toString()).isEqualTo("detached");

        assertThatThrownBy(() -> new PortletMode(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PortletMode name can not be NULL");
        assertThatThrownBy(() -> new WindowState(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WindowState name can not be NULL");
    }

    @Test
    void publicConstantsExposePortletTwoRequestResponseAndResourceNames() {
        assertThat(PortletRequest.USER_INFO).isEqualTo("javax.portlet.userinfo");
        assertThat(PortletRequest.ACTION_PHASE).isEqualTo("ACTION_PHASE");
        assertThat(PortletRequest.EVENT_PHASE).isEqualTo("EVENT_PHASE");
        assertThat(PortletRequest.RENDER_PHASE).isEqualTo("RENDER_PHASE");
        assertThat(PortletRequest.RESOURCE_PHASE).isEqualTo("RESOURCE_PHASE");
        assertThat(PortletRequest.LIFECYCLE_PHASE).isEqualTo("javax.portlet.lifecycle_phase");
        assertThat(PortletRequest.RENDER_PART).isEqualTo("javax.portlet.render_part");
        assertThat(PortletRequest.RENDER_HEADERS).isEqualTo("RENDER_HEADERS");
        assertThat(PortletRequest.RENDER_MARKUP).isEqualTo("RENDER_MARKUP");
        assertThat(PortletRequest.ACTION_SCOPE_ID).isEqualTo("javax.portlet.as");
        assertThat(ActionRequest.ACTION_NAME).isEqualTo("javax.portlet.action");
        assertThat(RenderRequest.ETAG).isEqualTo("portlet.ETag");
        assertThat(ResourceRequest.ETAG).isEqualTo("portlet.ETag");
        assertThat(ResourceResponse.HTTP_STATUS_CODE).isEqualTo("portlet.http-status-code");
        assertThat(MimeResponse.CACHE_SCOPE).isEqualTo("portlet.cache-scope");
        assertThat(MimeResponse.PUBLIC_SCOPE).isEqualTo("portlet.public-scope");
        assertThat(MimeResponse.PRIVATE_SCOPE).isEqualTo("portlet.private-scope");
        assertThat(MimeResponse.ETAG).isEqualTo("portlet.ETag");
        assertThat(MimeResponse.USE_CACHED_CONTENT).isEqualTo("portlet.use-cached-content");
        assertThat(MimeResponse.NAMESPACED_RESPONSE).isEqualTo("X-JAVAX-PORTLET-NAMESPACED-RESPONSE");
        assertThat(MimeResponse.MARKUP_HEAD_ELEMENT).isEqualTo("javax.portlet.markup.head.element");
        assertThat(ResourceURL.FULL).isEqualTo("cacheLevelFull");
        assertThat(ResourceURL.PORTLET).isEqualTo("cacheLevelPortlet");
        assertThat(ResourceURL.PAGE).isEqualTo("cacheLevelPage");
        assertThat(ResourceURL.SHARED).isEqualTo("javax.portlet.shared");
        assertThat(PortalContext.MARKUP_HEAD_ELEMENT_SUPPORT).isEqualTo("javax.portlet.markup.head.element.support");
        assertThat(PortletSession.APPLICATION_SCOPE).isEqualTo(1);
        assertThat(PortletSession.PORTLET_SCOPE).isEqualTo(2);
    }

    @Test
    void sessionUtilityDecodesPortletScopedAttributeNames() {
        String portletScopedName = "javax.portlet.p.sample-portlet?cart";
        String applicationScopedName = "cart";
        String malformedPortletName = "javax.portlet.p.sample-portlet";

        assertThat(PortletSessionUtil.decodeAttributeName(portletScopedName)).isEqualTo("cart");
        assertThat(PortletSessionUtil.decodeScope(portletScopedName)).isEqualTo(PortletSession.PORTLET_SCOPE);
        assertThat(PortletSessionUtil.decodeAttributeName(applicationScopedName)).isEqualTo(applicationScopedName);
        assertThat(PortletSessionUtil.decodeScope(applicationScopedName)).isEqualTo(PortletSession.APPLICATION_SCOPE);
        assertThat(PortletSessionUtil.decodeAttributeName(malformedPortletName)).isEqualTo(malformedPortletName);
        assertThat(PortletSessionUtil.decodeScope(malformedPortletName)).isEqualTo(PortletSession.APPLICATION_SCOPE);
    }

    @Test
    void exceptionsPreservePortletSpecificDetails() {
        Throwable cause = new IllegalArgumentException("invalid");
        WindowState detached = new WindowState("detached");
        PortletMode configure = new PortletMode("configure");

        PortletException messageOnly = new PortletException("top level");
        PortletException withCause = new PortletException("wrapped", cause);
        PortletException causeOnly = new PortletException(cause);
        UnavailableException permanent = new UnavailableException("maintenance");
        UnavailableException temporary = new UnavailableException("retry", 30);
        UnavailableException immediateRetry = new UnavailableException("retry", 0);
        WindowStateException stateException = new WindowStateException("unsupported state", cause, detached);
        PortletModeException modeException = new PortletModeException("unsupported mode", cause, configure);
        ValidatorException validatorException = new ValidatorException("failed", cause, List.of("theme", "layout"));

        assertThat(messageOnly).hasMessage("top level");
        assertThat(withCause).hasMessage("wrapped");
        assertThat(withCause.getCause()).isSameAs(cause);
        assertThat(causeOnly.getCause()).isSameAs(cause);
        assertThat(permanent.isPermanent()).isTrue();
        assertThat(permanent.getUnavailableSeconds()).isEqualTo(-1);
        assertThat(temporary.isPermanent()).isFalse();
        assertThat(temporary.getUnavailableSeconds()).isEqualTo(30);
        assertThat(immediateRetry.isPermanent()).isFalse();
        assertThat(immediateRetry.getUnavailableSeconds()).isEqualTo(-1);
        assertThat(stateException.getState()).isEqualTo(detached);
        assertThat(stateException.getCause()).isSameAs(cause);
        assertThat(modeException.getMode()).isEqualTo(configure);
        assertThat(modeException.getCause()).isSameAs(cause);
        assertThat(Collections.list(validatorException.getFailedKeys())).containsExactly("theme", "layout");
        assertThat(new ValidatorException("failed", null).getFailedKeys().hasMoreElements()).isFalse();
        assertThat(new ReadOnlyException("read only")).hasMessage("read only");
        assertThat(new PortletSecurityException("security")).hasMessage("security");
    }

    @Test
    void genericPortletDelegatesConfigAndDispatchesRenderLifecycle() throws Exception {
        RecordingPortletContext context = new RecordingPortletContext();
        TestPortletConfig config = new TestPortletConfig(context);
        RecordingGenericPortlet portlet = new RecordingGenericPortlet();
        RecordingHandler renderHandler = new RecordingHandler()
                .returns("getLocale", Locale.ENGLISH)
                .returns("getWindowState", WindowState.NORMAL)
                .returns("getPortletMode", PortletMode.VIEW);
        RecordingHandler responseHandler = new RecordingHandler()
                .returns("getWriter", new PrintWriter(new StringWriter(), true));

        portlet.init(config);
        portlet.render(proxy(RenderRequest.class, renderHandler), proxy(RenderResponse.class, responseHandler));

        assertThat(portlet.events).containsExactly("init", "headers", "view");
        assertThat(portlet.getPortletConfig()).isSameAs(config);
        assertThat(portlet.getPortletName()).isEqualTo("sample-portlet");
        assertThat(portlet.getPortletContext()).isSameAs(context);
        assertThat(portlet.getDefaultNamespace()).isEqualTo(DEFAULT_NAMESPACE);
        assertThat(portlet.getInitParameter("template")).isEqualTo("/WEB-INF/view.jsp");
        assertThat(portlet.getResourceBundle(Locale.ENGLISH).getString("javax.portlet.title"))
                .isEqualTo("Localized Sample Portlet");
        assertThat(Collections.list(portlet.getInitParameterNames())).containsExactly("template");
        assertThat(Collections.list(portlet.getPublicRenderParameterNames())).containsExactly("theme");
        assertThat(Collections.list(portlet.getPublishingEventQNames()))
                .containsExactly(new QName(DEFAULT_NAMESPACE, "published"));
        assertThat(Collections.list(portlet.getProcessingEventQNames()))
                .containsExactly(new QName(DEFAULT_NAMESPACE, "updated"));
        assertThat(Collections.list(portlet.getSupportedLocales())).containsExactly(Locale.ENGLISH, Locale.FRENCH);
        assertThat(portlet.getContainerRuntimeOptions().get("javax.portlet.escapeXml")).containsExactly("true");
        assertThat(responseHandler.arguments("setTitle").get(0)[0]).isEqualTo("Localized Sample Portlet");
        assertThat(responseHandler.arguments("setNextPossiblePortletModes").get(0)[0])
                .isEqualTo(List.of(PortletMode.EDIT, PortletMode.HELP));
    }

    @Test
    void genericPortletDispatchesStandardModesAndHandlesMinimizedAndUnknownModes() throws Exception {
        RecordingGenericPortlet portlet = initializedPortlet();

        renderPortlet(portlet, PortletMode.EDIT, null);
        renderPortlet(portlet, PortletMode.HELP, null);
        int eventsBeforeMinimized = portlet.events.size();
        renderPortlet(portlet, PortletMode.VIEW, WindowState.MINIMIZED);

        assertThat(portlet.events).contains("edit", "help");
        assertThat(portlet.events).hasSize(eventsBeforeMinimized + 1);
        assertThat(portlet.events.get(portlet.events.size() - 1)).isEqualTo("headers");

        PortletException exception = assertThrows(PortletException.class, () ->
                renderPortlet(portlet, new PortletMode("preview"), null));
        assertThat(exception).hasMessage("unknown portlet mode: preview");
    }

    @Test
    void genericPortletHonorsSeparateRenderHeaderAndMarkupParts() throws Exception {
        RecordingGenericPortlet portlet = initializedPortlet();
        RecordingRenderResponse headersResponse = new RecordingRenderResponse();

        portlet.render(new RenderPartRequest(PortletRequest.RENDER_HEADERS), headersResponse);

        assertThat(portlet.events).containsExactly("headers");
        assertThat(headersResponse.title).isEqualTo("Localized Sample Portlet");
        assertThat(headersResponse.nextPossiblePortletModes).containsExactly(PortletMode.EDIT, PortletMode.HELP);

        portlet.events.clear();
        RecordingRenderResponse markupResponse = new RecordingRenderResponse();

        portlet.render(new RenderPartRequest(PortletRequest.RENDER_MARKUP), markupResponse);

        assertThat(portlet.events).containsExactly("view");
        assertThat(markupResponse.title).isNull();
        assertThat(markupResponse.nextPossiblePortletModes).isNull();

        PortletException exception = assertThrows(PortletException.class, () ->
                portlet.render(new RenderPartRequest("unknown-part"), new RecordingRenderResponse()));
        assertThat(exception).hasMessage("Unknown value of the 'javax.portlet.render_part' request attribute");
    }

    @Test
    void genericPortletAnnotationBasedActionRenderAndEventDispatch() throws Exception {
        AnnotatedPortlet portlet = new AnnotatedPortlet();
        portlet.init(new TestPortletConfig(new RecordingPortletContext()));

        RecordingHandler actionRequest = new RecordingHandler()
                .returns("getParameter", ActionRequest.ACTION_NAME, "save");
        RecordingHandler actionResponse = new RecordingHandler();
        RecordingHandler renderRequest = new RecordingHandler()
                .returns("getLocale", Locale.ENGLISH)
                .returns("getWindowState", WindowState.NORMAL)
                .returns("getPortletMode", new PortletMode("preview"));
        StringWriter renderWriter = new StringWriter();
        RecordingHandler renderResponse = new RecordingHandler()
                .returns("getWriter", new PrintWriter(renderWriter, true));
        RecordingHandler eventRequest = eventRequestHandler(new QName(DEFAULT_NAMESPACE, "updated"));
        RecordingHandler eventResponse = new RecordingHandler();
        RecordingHandler unmatchedEventRequest = eventRequestHandler(new QName(DEFAULT_NAMESPACE, "other"));
        RecordingHandler unmatchedEventResponse = new RecordingHandler();

        portlet.processAction(proxy(ActionRequest.class, actionRequest), proxy(ActionResponse.class, actionResponse));
        portlet.render(proxy(RenderRequest.class, renderRequest), proxy(RenderResponse.class, renderResponse));
        portlet.processEvent(proxy(EventRequest.class, eventRequest), proxy(EventResponse.class, eventResponse));
        portlet.processEvent(
                proxy(EventRequest.class, unmatchedEventRequest), proxy(EventResponse.class, unmatchedEventResponse));

        assertThat(portlet.events).containsExactly("save-action", "preview-render", "updated-event");
        assertThat(actionResponse.arguments("setRenderParameter").get(0)).containsExactly("saved", "true");
        assertThat(renderWriter.toString()).isEqualTo("preview");
        assertThat(eventResponse.arguments("setRenderParameter").get(0)).containsExactly("event", "updated");
        assertThat(unmatchedEventResponse.arguments("setRenderParameters").get(0)[0])
                .isSameAs(unmatchedEventRequest.proxy);
    }

    @Test
    void genericPortletServeResourceForwardsToContextDispatcher() throws Exception {
        RecordingPortletContext context = new RecordingPortletContext();
        RecordingGenericPortlet portlet = new RecordingGenericPortlet();
        RecordingHandler resourceRequest = new RecordingHandler().returns("getResourceID", "/assets/view.txt");
        RecordingHandler resourceResponse = new RecordingHandler();

        portlet.init(new TestPortletConfig(context));
        portlet.serveResource(
                proxy(ResourceRequest.class, resourceRequest), proxy(ResourceResponse.class, resourceResponse));

        assertThat(context.forwardedTargets).containsExactly("/assets/view.txt");
    }

    @Test
    void requestWrappersDelegateReadsAndMutationsToCurrentRequest() throws Exception {
        RecordingHandler first = requestHandler(PortletMode.VIEW, WindowState.NORMAL)
                .returns("getETag", "etag-1")
                .returns("getMethod", "POST")
                .returns("getContentLength", 11)
                .returns("getContentType", "text/plain")
                .returns("getCharacterEncoding", "UTF-8")
                .returns("getPortletInputStream", new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)))
                .returns("getReader", new BufferedReader(new StringReader("reader")))
                .returns("getResourceID", "resource-1")
                .returns("getCacheability", ResourceURL.PORTLET)
                .returns("getPrivateRenderParameterMap", Map.of("from", new String[] {"render"}));
        RecordingHandler second = requestHandler(PortletMode.EDIT, WindowState.MAXIMIZED)
                .returns("getETag", "etag-2");
        PortletRequestWrapper portletWrapper = new PortletRequestWrapper(proxy(PortletRequest.class, first));
        RenderRequestWrapper renderWrapper = new RenderRequestWrapper(proxy(RenderRequest.class, first));
        ActionRequestWrapper actionWrapper = new ActionRequestWrapper(proxy(ActionRequest.class, first));
        EventRequestWrapper eventWrapper = new EventRequestWrapper(proxy(EventRequest.class, first));
        ResourceRequestWrapper resourceWrapper = new ResourceRequestWrapper(proxy(ResourceRequest.class, first));

        portletWrapper.setAttribute("cart", "42");
        portletWrapper.removeAttribute("old");
        RenderRequest secondRenderRequest = proxy(RenderRequest.class, second);
        portletWrapper.setRequest(proxy(PortletRequest.class, second));
        renderWrapper.setRequest(secondRenderRequest);

        assertThat(portletWrapper.getPortletMode()).isEqualTo(PortletMode.EDIT);
        assertThat(renderWrapper.getRequest()).isSameAs(secondRenderRequest);
        assertThat(renderWrapper.getPortletMode()).isEqualTo(PortletMode.VIEW);
        assertThat(renderWrapper.getETag()).isEqualTo("etag-2");
        assertThat(actionWrapper.getMethod()).isEqualTo("POST");
        assertThat(actionWrapper.getContentLength()).isEqualTo(11);
        assertThat(actionWrapper.getReader().readLine()).isEqualTo("reader");
        assertThat(eventWrapper.getMethod()).isEqualTo("POST");
        assertThat(resourceWrapper.getResourceID()).isEqualTo("resource-1");
        assertThat(resourceWrapper.getCacheability()).isEqualTo(ResourceURL.PORTLET);
        assertThat(resourceWrapper.getPrivateRenderParameterMap().get("private")).containsExactly("1");
        assertThat(first.arguments("setAttribute").get(0)).containsExactly("cart", "42");
        assertThat(first.arguments("removeAttribute").get(0)).containsExactly("old");
        assertThatThrownBy(() -> portletWrapper.setRequest(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> renderWrapper.setRequest(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void responseWrappersDelegateStateMimeAndResourceOperations() throws Exception {
        StringWriter writer = new StringWriter();
        RecordingHandler response = new RecordingHandler()
                .returns("getNamespace", "sample_")
                .returns("encodeURL", "/path", "/path;encoded=true")
                .returns("getWriter", new PrintWriter(writer, true))
                .returns("getContentType", "text/html")
                .returns("getCharacterEncoding", "UTF-8")
                .returns("getLocale", Locale.ENGLISH)
                .returns("getBufferSize", 512)
                .returns("getPortletOutputStream", java.io.OutputStream.nullOutputStream())
                .returns("isCommitted", false)
                .returns("getCacheControl", proxy(CacheControl.class, new RecordingHandler()))
                .returns("createRenderURL", proxy(PortletURL.class, new RecordingHandler()))
                .returns("createActionURL", proxy(PortletURL.class, new RecordingHandler()))
                .returns("createResourceURL", proxy(ResourceURL.class, new RecordingHandler()))
                .returns("getPortletMode", PortletMode.EDIT)
                .returns("getWindowState", WindowState.MAXIMIZED)
                .returns("getRenderParameterMap", Map.of("tab", new String[] {"details"}));
        RenderResponseWrapper renderWrapper = new RenderResponseWrapper(proxy(RenderResponse.class, response));
        ResourceResponseWrapper resourceWrapper = new ResourceResponseWrapper(proxy(ResourceResponse.class, response));
        ActionResponseWrapper actionWrapper = new ActionResponseWrapper(proxy(ActionResponse.class, response));
        EventResponseWrapper eventWrapper = new EventResponseWrapper(proxy(EventResponse.class, response));

        renderWrapper.setTitle("Dashboard");
        renderWrapper.setContentType("text/plain");
        renderWrapper.setNextPossiblePortletModes(List.of(PortletMode.VIEW));
        renderWrapper.getWriter().write("body");
        renderWrapper.flushBuffer();
        renderWrapper.resetBuffer();
        resourceWrapper.setLocale(Locale.FRENCH);
        resourceWrapper.setCharacterEncoding("ISO-8859-1");
        resourceWrapper.setContentLength(128);
        actionWrapper.setWindowState(WindowState.MAXIMIZED);
        actionWrapper.setPortletMode(PortletMode.EDIT);
        actionWrapper.setRenderParameter("tab", "details");
        actionWrapper.setRenderParameters(Map.of("view", new String[] {"summary"}));
        actionWrapper.setEvent(new QName(DEFAULT_NAMESPACE, "saved"), "payload");
        actionWrapper.sendRedirect("/done", "token");
        eventWrapper.setRenderParameters(proxy(EventRequest.class, new RecordingHandler()));
        eventWrapper.removePublicRenderParameter("obsolete");

        assertThat(renderWrapper.getNamespace()).isEqualTo("sample_");
        assertThat(renderWrapper.encodeURL("/path")).isEqualTo("/path;encoded=true");
        assertThat(renderWrapper.getContentType()).isEqualTo("text/html");
        assertThat(renderWrapper.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(renderWrapper.getLocale()).isEqualTo(Locale.ENGLISH);
        assertThat(renderWrapper.getBufferSize()).isEqualTo(512);
        assertThat(renderWrapper.isCommitted()).isFalse();
        assertThat(writer.toString()).isEqualTo("body");
        assertThat(actionWrapper.getPortletMode()).isEqualTo(PortletMode.EDIT);
        assertThat(actionWrapper.getWindowState()).isEqualTo(WindowState.MAXIMIZED);
        assertThat(actionWrapper.getRenderParameterMap().get("tab")).containsExactly("details");
        assertThat(response.arguments("setTitle").get(0)).containsExactly("Dashboard");
        assertThat(response.arguments("setContentLength").get(0)).containsExactly(128);
        assertThat(response.arguments("sendRedirect").get(0)).containsExactly("/done", "token");
        assertThat(response.arguments("removePublicRenderParameter").get(0)).containsExactly("obsolete");
        assertThatThrownBy(() -> renderWrapper.setResponse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> actionWrapper.setResponse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preferencesValidatorAndMutablePublicInterfacesCoverUrlsSessionsAndCacheControl() throws Exception {
        RecordingPortletPreferences preferences = new RecordingPortletPreferences(Map.of("theme", "light"));
        PreferencesValidator validator = portletPreferences -> {
            List<String> failedKeys = new ArrayList<>();
            if (!List.of("light", "dark").contains(portletPreferences.getValue("theme", ""))) {
                failedKeys.add("theme");
            }
            if (portletPreferences.getValues("columns", new String[0]).length == 0) {
                failedKeys.add("columns");
            }
            if (!failedKeys.isEmpty()) {
                throw new ValidatorException("invalid preferences", failedKeys);
            }
        };
        RecordingBaseURL url = new RecordingBaseURL("/resource");
        RecordingCacheControl cacheControl = new RecordingCacheControl();
        RecordingPortletSession session = new RecordingPortletSession(new RecordingPortletContext());

        preferences.setValues("columns", new String[] {"name", "status"});
        validator.validate(preferences);
        preferences.setValue("theme", "contrast");
        preferences.reset("columns");
        url.setParameter("id", "42");
        url.setParameter("tag", new String[] {"a", "b"});
        url.setProperty("Cache-Control", "private");
        url.addProperty("Vary", "Accept");
        url.setSecure(true);
        StringWriter urlWriter = new StringWriter();
        url.write(urlWriter, true);
        cacheControl.setExpirationTime(60);
        cacheControl.setPublicScope(true);
        cacheControl.setETag("abc");
        cacheControl.setUseCachedContent(true);
        session.setAttribute("cart", "42");
        session.setAttribute("shared", "yes", PortletSession.APPLICATION_SCOPE);
        session.setMaxInactiveInterval(600);

        ValidatorException exception = assertThrows(ValidatorException.class, () -> validator.validate(preferences));
        assertThat(Collections.list(exception.getFailedKeys())).containsExactly("theme", "columns");
        assertThat(Collections.list(preferences.getNames())).containsExactly("theme");
        assertThat(preferences.getMap()).containsKey("theme");
        assertThat(url.getParameterMap().get("tag")).containsExactly("a", "b");
        assertThat(url.toString()).isEqualTo("/resource?id=42&tag=a,b;secure=true");
        assertThat(urlWriter.toString()).isEqualTo(url.toString());
        assertThat(cacheControl.getExpirationTime()).isEqualTo(60);
        assertThat(cacheControl.isPublicScope()).isTrue();
        assertThat(cacheControl.getETag()).isEqualTo("abc");
        assertThat(cacheControl.useCachedContent()).isTrue();
        assertThat(session.getAttribute("cart")).isEqualTo("42");
        assertThat(session.getAttribute("shared", PortletSession.APPLICATION_SCOPE)).isEqualTo("yes");
        assertThat(session.getAttributeMap()).containsEntry("cart", "42");
        assertThat(session.getAttributeMap(PortletSession.APPLICATION_SCOPE)).containsEntry("shared", "yes");
        assertThat(session.getMaxInactiveInterval()).isEqualTo(600);
        session.invalidate();
        assertThat(session.isNew()).isFalse();
    }

    @Test
    void contextPortalAndDispatcherInterfacesExposeResourcesAndDiagnostics() throws Exception {
        RecordingPortletContext context = new RecordingPortletContext();
        RecordingHandler renderResponse = new RecordingHandler()
                .returns("getWriter", new PrintWriter(new StringWriter(), true));
        RenderRequest renderRequest = proxy(RenderRequest.class, requestHandler(PortletMode.VIEW, WindowState.NORMAL));
        RenderResponse response = proxy(RenderResponse.class, renderResponse);
        PortalContext portalContext = new TestPortalContext();

        context.getRequestDispatcher("/WEB-INF/view.jsp").include(renderRequest, response);
        context.getNamedDispatcher("header").include(renderRequest, response);
        context.getRequestDispatcher("/WEB-INF/forward.jsp")
                .forward(renderRequest, proxy(PortletResponse.class, new RecordingHandler()));
        context.log("rendered");
        context.log("failed", new IllegalStateException("boom"));
        context.setAttribute("counter", 1);
        context.removeAttribute("missing");

        assertThat(new String(context.getResourceAsStream("/index.html").readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("/index.html");
        assertThat(context.getServerInfo()).isEqualTo("test-portal/2.0");
        assertThat(context.getMajorVersion()).isEqualTo(2);
        assertThat(context.getMinorVersion()).isEqualTo(0);
        assertThat(context.getMimeType("/index.html")).isEqualTo("text/html");
        assertThat(context.getMimeType("/assets/app.bin")).isEqualTo("application/octet-stream");
        assertThat(context.getRealPath("/index.html")).isEqualTo("/portal/index.html");
        assertThat(context.getResource("/index.html")).isEqualTo(URI.create("file:/portal/index.html").toURL());
        assertThat(context.getResourcePaths("/public")).containsExactly("/public/index.html");
        assertThat(context.getPortletContextName()).isEqualTo("sample-context");
        assertThat(context.getInitParameter("application")).isEqualTo("sample");
        assertThat(Collections.list(context.getInitParameterNames())).containsExactly("application");
        assertThat(Collections.list(context.getContainerRuntimeOptions())).containsExactly("javax.portlet.escapeXml");
        assertThat(context.getAttribute("counter")).isEqualTo(1);
        assertThat(context.getAttribute("lastLog")).isEqualTo("failed:IllegalStateException");
        assertThat(context.includedTargets).containsExactly("/WEB-INF/view.jsp", "header");
        assertThat(context.forwardedTargets).containsExactly("/WEB-INF/forward.jsp");
        assertThat(portalContext.getProperty("portal.vendor")).isEqualTo("metadata-tests");
        assertThat(Collections.list(portalContext.getPropertyNames())).containsExactly("portal.vendor");
        assertThat(Collections.list(portalContext.getSupportedPortletModes()))
                .containsExactly(PortletMode.VIEW, PortletMode.EDIT, PortletMode.HELP);
        assertThat(Collections.list(portalContext.getSupportedWindowStates()))
                .containsExactly(WindowState.NORMAL, WindowState.MAXIMIZED, WindowState.MINIMIZED);
        assertThat(portalContext.getPortalInfo()).isEqualTo("metadata-test-portal/2.0");
    }

    @Test
    void portletFiltersInitializeWrapRequestsAndContinueLifecycleChains() throws Exception {
        RecordingPortletContext context = new RecordingPortletContext();
        TestFilterConfig config = new TestFilterConfig("audit-filter", context);
        RecordingPortletFilter filter = new RecordingPortletFilter();
        RecordingFilterChain chain = new RecordingFilterChain();
        RecordingFilterResponse actionResponse = new RecordingFilterResponse();
        RecordingFilterResponse renderResponse = new RecordingFilterResponse();
        RecordingFilterResponse eventResponse = new RecordingFilterResponse();
        RecordingFilterResponse resourceResponse = new RecordingFilterResponse();
        FilterPortletRequest request = new FilterPortletRequest();

        filter.init(config);
        filter.doFilter((ActionRequest) request, (ActionResponse) actionResponse, chain);
        filter.doFilter((RenderRequest) request, (RenderResponse) renderResponse, chain);
        filter.doFilter((EventRequest) request, (EventResponse) eventResponse, chain);
        filter.doFilter((ResourceRequest) request, (ResourceResponse) resourceResponse, chain);
        filter.destroy();

        assertThat(filter.events).containsExactly(
                "init:audit-filter", "before-action", "after-action", "before-render", "after-render",
                "before-event", "after-event", "before-resource", "after-resource", "destroy");
        assertThat(context.getAttribute("audit-filter.prefix")).isEqualTo("filtered");
        assertThat(Collections.list(config.getInitParameterNames())).containsExactly("prefix");
        assertThat(chain.events).containsExactly(
                "action:filtered-save", "render:filtered-mode", "event:filtered-updated", "resource:filtered-resource");
        assertThat(actionResponse.renderParameters).containsEntry("action", new String[] {"filtered-save"});
        assertThat(renderResponse.title).isEqualTo("Filtered filtered-mode");
        assertThat(eventResponse.renderParameters).containsEntry("event", new String[] {"filtered-updated"});
        assertThat(resourceResponse.properties).containsEntry("X-Resource-ID", "filtered-resource");
    }

    private static RecordingGenericPortlet initializedPortlet() throws PortletException {
        RecordingGenericPortlet portlet = new RecordingGenericPortlet();
        portlet.init(new TestPortletConfig(new RecordingPortletContext()));
        portlet.events.clear();
        return portlet;
    }

    private static void renderPortlet(RecordingGenericPortlet portlet, PortletMode mode, WindowState state)
            throws Exception {
        WindowState windowState = state == null ? WindowState.NORMAL : state;
        RecordingHandler request = new RecordingHandler()
                .returns("getLocale", Locale.ENGLISH)
                .returns("getWindowState", windowState)
                .returns("getPortletMode", mode);
        RecordingHandler response = new RecordingHandler()
                .returns("getWriter", new PrintWriter(new StringWriter(), true));
        portlet.render(proxy(RenderRequest.class, request), proxy(RenderResponse.class, response));
    }

    private static RecordingHandler eventRequestHandler(QName qName) {
        RecordingHandler event = new RecordingHandler()
                .returns("getQName", qName)
                .returns("getName", qName.getLocalPart())
                .returns("getValue", "payload");
        Event eventProxy = proxy(Event.class, event);
        return requestHandler(PortletMode.VIEW, WindowState.NORMAL)
                .returns("getEvent", eventProxy)
                .returns("getMethod", "EVENT");
    }

    private static RecordingHandler requestHandler(PortletMode mode, WindowState state) {
        return new RecordingHandler()
                .returns("isWindowStateAllowed", Boolean.TRUE)
                .returns("isPortletModeAllowed", Boolean.TRUE)
                .returns("getPortletMode", mode)
                .returns("getWindowState", state)
                .returns("getPreferences", new RecordingPortletPreferences(Map.of("theme", "light")))
                .returns("getPortletSession", new RecordingPortletSession(new RecordingPortletContext()))
                .returns("getProperty", "X-Test", "enabled")
                .returns("getProperties", Collections.enumeration(List.of("enabled")))
                .returns("getPropertyNames", Collections.enumeration(List.of("X-Test")))
                .returns("getPortalContext", new TestPortalContext())
                .returns("getAuthType", PortletRequest.BASIC_AUTH)
                .returns("getContextPath", "/sample")
                .returns("getRemoteUser", "tester")
                .returns("getUserPrincipal", (Principal) () -> "tester")
                .returns("isUserInRole", Boolean.TRUE)
                .returns("getAttribute", null)
                .returns("getAttributeNames", Collections.emptyEnumeration())
                .returns("getParameter", "page", "1")
                .returns("getParameterNames", Collections.enumeration(List.of("page")))
                .returns("getParameterValues", new String[] {"1"})
                .returns("getParameterMap", Map.of("page", new String[] {"1"}))
                .returns("isSecure", Boolean.TRUE)
                .returns("getRequestedSessionId", "session-1")
                .returns("isRequestedSessionIdValid", Boolean.TRUE)
                .returns("getResponseContentType", "text/html")
                .returns("getResponseContentTypes", Collections.enumeration(List.of("text/html", "text/plain")))
                .returns("getLocale", Locale.ENGLISH)
                .returns("getLocales", Collections.enumeration(List.of(Locale.ENGLISH)))
                .returns("getScheme", "https")
                .returns("getServerName", "localhost")
                .returns("getServerPort", 443)
                .returns("getWindowID", "window-1")
                .returns("getCookies", null)
                .returns("getPrivateParameterMap", Map.of("private", new String[] {"1"}))
                .returns("getPublicParameterMap", Map.of("public", new String[] {"2"}));
    }

    private static class RenderPartRequest implements RenderRequest {
        private final Object renderPart;

        RenderPartRequest(Object renderPart) {
            this.renderPart = renderPart;
        }

        @Override
        public String getETag() {
            return "etag";
        }

        @Override
        public boolean isWindowStateAllowed(WindowState state) {
            return true;
        }

        @Override
        public boolean isPortletModeAllowed(PortletMode mode) {
            return true;
        }

        @Override
        public PortletMode getPortletMode() {
            return PortletMode.VIEW;
        }

        @Override
        public WindowState getWindowState() {
            return WindowState.NORMAL;
        }

        @Override
        public PortletPreferences getPreferences() {
            return new RecordingPortletPreferences(Map.of("theme", "light"));
        }

        @Override
        public PortletSession getPortletSession() {
            return new RecordingPortletSession(new RecordingPortletContext());
        }

        @Override
        public PortletSession getPortletSession(boolean create) {
            return create ? getPortletSession() : null;
        }

        @Override
        public String getProperty(String name) {
            return null;
        }

        @Override
        public Enumeration<String> getProperties(String name) {
            return Collections.emptyEnumeration();
        }

        @Override
        public Enumeration<String> getPropertyNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public PortalContext getPortalContext() {
            return new TestPortalContext();
        }

        @Override
        public String getAuthType() {
            return PortletRequest.BASIC_AUTH;
        }

        @Override
        public String getContextPath() {
            return "/sample";
        }

        @Override
        public String getRemoteUser() {
            return "tester";
        }

        @Override
        public Principal getUserPrincipal() {
            return () -> "tester";
        }

        @Override
        public boolean isUserInRole(String role) {
            return true;
        }

        @Override
        public Object getAttribute(String name) {
            return PortletRequest.RENDER_PART.equals(name) ? renderPart : null;
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(List.of(PortletRequest.RENDER_PART));
        }

        @Override
        public String getParameter(String name) {
            return null;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public String[] getParameterValues(String name) {
            return null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Map.of();
        }

        @Override
        public boolean isSecure() {
            return true;
        }

        @Override
        public void setAttribute(String name, Object object) {
        }

        @Override
        public void removeAttribute(String name) {
        }

        @Override
        public String getRequestedSessionId() {
            return "session-1";
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return true;
        }

        @Override
        public String getResponseContentType() {
            return "text/html";
        }

        @Override
        public Enumeration<String> getResponseContentTypes() {
            return Collections.enumeration(List.of("text/html"));
        }

        @Override
        public Locale getLocale() {
            return Locale.ENGLISH;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return Collections.enumeration(List.of(Locale.ENGLISH));
        }

        @Override
        public String getScheme() {
            return "https";
        }

        @Override
        public String getServerName() {
            return "localhost";
        }

        @Override
        public int getServerPort() {
            return 443;
        }

        @Override
        public String getWindowID() {
            return "window-1";
        }

        @Override
        public javax.servlet.http.Cookie[] getCookies() {
            return null;
        }

        @Override
        public Map<String, String[]> getPrivateParameterMap() {
            return Map.of();
        }

        @Override
        public Map<String, String[]> getPublicParameterMap() {
            return Map.of();
        }
    }

    private static class RecordingRenderResponse implements RenderResponse {
        private String title;
        private Collection<PortletMode> nextPossiblePortletModes;

        @Override
        public void setTitle(String title) {
            this.title = title;
        }

        @Override
        public void setNextPossiblePortletModes(Collection<PortletMode> portletModes) {
            nextPossiblePortletModes = portletModes;
        }

        @Override
        public String getContentType() {
            return "text/html";
        }

        @Override
        public void setContentType(String type) {
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public PrintWriter getWriter() {
            return new PrintWriter(new StringWriter(), true);
        }

        @Override
        public Locale getLocale() {
            return Locale.ENGLISH;
        }

        @Override
        public void setBufferSize(int size) {
        }

        @Override
        public int getBufferSize() {
            return 0;
        }

        @Override
        public void flushBuffer() {
        }

        @Override
        public void resetBuffer() {
        }

        @Override
        public boolean isCommitted() {
            return false;
        }

        @Override
        public void reset() {
        }

        @Override
        public java.io.OutputStream getPortletOutputStream() {
            return java.io.OutputStream.nullOutputStream();
        }

        @Override
        public PortletURL createRenderURL() {
            return null;
        }

        @Override
        public PortletURL createActionURL() {
            return null;
        }

        @Override
        public ResourceURL createResourceURL() {
            return null;
        }

        @Override
        public CacheControl getCacheControl() {
            return new RecordingCacheControl();
        }

        @Override
        public void addProperty(String key, String value) {
        }

        @Override
        public void setProperty(String key, String value) {
        }

        @Override
        public String encodeURL(String path) {
            return path;
        }

        @Override
        public String getNamespace() {
            return "sample_";
        }

        @Override
        public void addProperty(javax.servlet.http.Cookie cookie) {
        }

        @Override
        public void addProperty(String key, org.w3c.dom.Element element) {
        }

        @Override
        public org.w3c.dom.Element createElement(String tagName) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> api, RecordingHandler handler) {
        T proxy = (T) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, handler);
        handler.proxy = proxy;
        return proxy;
    }

    private static class RecordingHandler implements InvocationHandler {
        private final Map<String, Object> defaultReturnValues = new HashMap<>();
        private final Map<String, Map<List<Object>, Object>> argumentReturnValues = new HashMap<>();
        private final Map<String, List<Object[]>> calls = new LinkedHashMap<>();
        private Object proxy;

        RecordingHandler returns(String methodName, Object value) {
            defaultReturnValues.put(methodName, value);
            return this;
        }

        RecordingHandler returns(String methodName, Object argument, Object value) {
            argumentReturnValues.computeIfAbsent(methodName, ignored -> new HashMap<>())
                    .put(List.of(argument), value);
            return this;
        }

        List<Object[]> arguments(String methodName) {
            return calls.getOrDefault(methodName, List.of());
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            Object[] invocationArguments = args == null ? new Object[0] : args.clone();
            calls.computeIfAbsent(method.getName(), ignored -> new ArrayList<>()).add(invocationArguments);
            if (method.getDeclaringClass().equals(Object.class)) {
                return invokeObjectMethod(proxy, method, invocationArguments);
            }
            Map<List<Object>, Object> byArguments = argumentReturnValues.get(method.getName());
            if (byArguments != null && byArguments.containsKey(Arrays.asList(invocationArguments))) {
                return byArguments.get(Arrays.asList(invocationArguments));
            }
            if (defaultReturnValues.containsKey(method.getName())) {
                return defaultReturnValues.get(method.getName());
            }
            return defaultValue(method.getReturnType());
        }

        private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            if ("toString".equals(method.getName())) {
                return proxy.getClass().getInterfaces()[0].getSimpleName() + " proxy";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            throw new IllegalStateException(method.getName());
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType.equals(Void.TYPE)) {
                return null;
            }
            if (returnType.equals(Boolean.TYPE)) {
                return false;
            }
            if (returnType.equals(Integer.TYPE)) {
                return 0;
            }
            if (returnType.equals(Long.TYPE)) {
                return 0L;
            }
            if (returnType.equals(Float.TYPE)) {
                return 0.0f;
            }
            if (returnType.equals(Double.TYPE)) {
                return 0.0d;
            }
            if (returnType.equals(Short.TYPE)) {
                return (short) 0;
            }
            if (returnType.equals(Byte.TYPE)) {
                return (byte) 0;
            }
            if (returnType.equals(Character.TYPE)) {
                return '\0';
            }
            return null;
        }
    }

    public static class AnnotatedPortlet extends GenericPortlet {
        private final List<String> events = new ArrayList<>();

        @ProcessAction(name = "save")
        public void save(ActionRequest request, ActionResponse response) {
            events.add("save-action");
            response.setRenderParameter("saved", "true");
        }

        @RenderMode(name = "preview")
        public void preview(RenderRequest request, RenderResponse response) throws IOException {
            events.add("preview-render");
            response.getWriter().write("preview");
        }

        @ProcessEvent(qname = "{urn:test}updated")
        public void updated(EventRequest request, EventResponse response) {
            events.add("updated-event");
            response.setRenderParameter("event", request.getEvent().getName());
        }
    }

    private static class RecordingGenericPortlet extends GenericPortlet {
        private final List<String> events = new ArrayList<>();

        @Override
        public void init() {
            events.add("init");
        }

        @Override
        protected void doHeaders(RenderRequest request, RenderResponse response) {
            events.add("headers");
        }

        @Override
        protected Collection<PortletMode> getNextPossiblePortletModes(RenderRequest request) {
            return List.of(PortletMode.EDIT, PortletMode.HELP);
        }

        @Override
        protected void doView(RenderRequest request, RenderResponse response) {
            events.add("view");
        }

        @Override
        protected void doEdit(RenderRequest request, RenderResponse response) {
            events.add("edit");
        }

        @Override
        protected void doHelp(RenderRequest request, RenderResponse response) {
            events.add("help");
        }
    }

    private static class RecordingPortletFilter
            implements ActionFilter, RenderFilter, EventFilter, ResourceFilter {
        private final List<String> events = new ArrayList<>();

        @Override
        public void init(FilterConfig filterConfig) {
            events.add("init:" + filterConfig.getFilterName());
            filterConfig.getPortletContext()
                    .setAttribute(filterConfig.getFilterName() + ".prefix", filterConfig.getInitParameter("prefix"));
        }

        @Override
        public void doFilter(ActionRequest request, ActionResponse response, FilterChain chain)
                throws IOException, PortletException {
            events.add("before-action");
            chain.doFilter(new ActionNameOverridingRequest(request), response);
            events.add("after-action");
        }

        @Override
        public void doFilter(RenderRequest request, RenderResponse response, FilterChain chain)
                throws IOException, PortletException {
            events.add("before-render");
            chain.doFilter(new RenderModeOverridingRequest(request), response);
            events.add("after-render");
        }

        @Override
        public void doFilter(EventRequest request, EventResponse response, FilterChain chain)
                throws IOException, PortletException {
            events.add("before-event");
            chain.doFilter(new EventOverridingRequest(request), response);
            events.add("after-event");
        }

        @Override
        public void doFilter(ResourceRequest request, ResourceResponse response, FilterChain chain)
                throws IOException, PortletException {
            events.add("before-resource");
            chain.doFilter(new ResourceIdOverridingRequest(request), response);
            events.add("after-resource");
        }

        @Override
        public void destroy() {
            events.add("destroy");
        }
    }

    private static class RecordingFilterChain implements FilterChain {
        private final List<String> events = new ArrayList<>();

        @Override
        public void doFilter(ActionRequest request, ActionResponse response) {
            String action = request.getParameter(ActionRequest.ACTION_NAME);
            events.add("action:" + action);
            response.setRenderParameter("action", action);
        }

        @Override
        public void doFilter(RenderRequest request, RenderResponse response) {
            String mode = request.getPortletMode().toString();
            events.add("render:" + mode);
            response.setTitle("Filtered " + mode);
        }

        @Override
        public void doFilter(EventRequest request, EventResponse response) {
            String eventName = request.getEvent().getName();
            events.add("event:" + eventName);
            response.setRenderParameter("event", eventName);
        }

        @Override
        public void doFilter(ResourceRequest request, ResourceResponse response) {
            String resourceId = request.getResourceID();
            events.add("resource:" + resourceId);
            response.setProperty("X-Resource-ID", resourceId);
        }
    }

    private static class ActionNameOverridingRequest extends ActionRequestWrapper {
        ActionNameOverridingRequest(ActionRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            return ActionRequest.ACTION_NAME.equals(name) ? "filtered-save" : super.getParameter(name);
        }
    }

    private static class RenderModeOverridingRequest extends RenderRequestWrapper {
        RenderModeOverridingRequest(RenderRequest request) {
            super(request);
        }

        @Override
        public PortletMode getPortletMode() {
            return new PortletMode("filtered-mode");
        }
    }

    private static class EventOverridingRequest extends EventRequestWrapper {
        EventOverridingRequest(EventRequest request) {
            super(request);
        }

        @Override
        public Event getEvent() {
            return new TestEvent(new QName(DEFAULT_NAMESPACE, "filtered-updated"), "payload");
        }
    }

    private static class ResourceIdOverridingRequest extends ResourceRequestWrapper {
        ResourceIdOverridingRequest(ResourceRequest request) {
            super(request);
        }

        @Override
        public String getResourceID() {
            return "filtered-resource";
        }
    }

    private static class TestEvent implements Event {
        private final QName qName;
        private final Serializable value;

        TestEvent(QName qName, Serializable value) {
            this.qName = qName;
            this.value = value;
        }

        @Override
        public QName getQName() {
            return qName;
        }

        @Override
        public String getName() {
            return qName.getLocalPart();
        }

        @Override
        public Serializable getValue() {
            return value;
        }
    }

    private static class FilterPortletRequest extends RenderPartRequest
            implements ActionRequest, EventRequest, ResourceRequest {
        FilterPortletRequest() {
            super(PortletRequest.RENDER_MARKUP);
        }

        @Override
        public String getParameter(String name) {
            return ActionRequest.ACTION_NAME.equals(name) ? "save" : super.getParameter(name);
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public int getContentLength() {
            return 0;
        }

        @Override
        public String getContentType() {
            return "text/plain";
        }

        @Override
        public InputStream getPortletInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new StringReader(""));
        }

        @Override
        public void setCharacterEncoding(String encoding) {
        }

        @Override
        public String getMethod() {
            return "POST";
        }

        @Override
        public Event getEvent() {
            return new TestEvent(new QName(DEFAULT_NAMESPACE, "updated"), "payload");
        }

        @Override
        public String getResourceID() {
            return "original-resource";
        }

        @Override
        public Map<String, String[]> getPrivateRenderParameterMap() {
            return Map.of("view", new String[] {"details"});
        }

        @Override
        public String getCacheability() {
            return ResourceURL.PORTLET;
        }
    }

    private static class RecordingFilterResponse
            implements ActionResponse, EventResponse, RenderResponse, ResourceResponse {
        private final Map<String, String[]> renderParameters = new LinkedHashMap<>();
        private final Map<String, String> properties = new LinkedHashMap<>();
        private String title;
        private PortletMode portletMode = PortletMode.VIEW;
        private WindowState windowState = WindowState.NORMAL;
        private Locale locale = Locale.ENGLISH;
        private String characterEncoding = "UTF-8";
        private String contentType = "text/html";

        @Override
        public void setWindowState(WindowState windowState) {
            this.windowState = windowState;
        }

        @Override
        public void setPortletMode(PortletMode portletMode) {
            this.portletMode = portletMode;
        }

        @Override
        public void setRenderParameters(Map<String, String[]> parameters) {
            renderParameters.clear();
            renderParameters.putAll(parameters);
        }

        @Override
        public void setRenderParameter(String key, String value) {
            renderParameters.put(key, new String[] {value});
        }

        @Override
        public void setRenderParameter(String key, String[] values) {
            renderParameters.put(key, values);
        }

        @Override
        public void setEvent(QName name, Serializable value) {
            properties.put("event", name.getLocalPart() + ":" + value);
        }

        @Override
        public void setEvent(String name, Serializable value) {
            properties.put("event", name + ":" + value);
        }

        @Override
        public Map<String, String[]> getRenderParameterMap() {
            return renderParameters;
        }

        @Override
        public PortletMode getPortletMode() {
            return portletMode;
        }

        @Override
        public WindowState getWindowState() {
            return windowState;
        }

        @Override
        public void removePublicRenderParameter(String name) {
            renderParameters.remove(name);
        }

        @Override
        public void setRenderParameters(EventRequest request) {
            setRenderParameters(request.getParameterMap());
        }

        @Override
        public void sendRedirect(String location) {
            properties.put("redirect", location);
        }

        @Override
        public void sendRedirect(String location, String renderUrlParamName) {
            properties.put("redirect", location + ":" + renderUrlParamName);
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        @Override
        public String getCharacterEncoding() {
            return characterEncoding;
        }

        @Override
        public PrintWriter getWriter() {
            return new PrintWriter(new StringWriter(), true);
        }

        @Override
        public Locale getLocale() {
            return locale;
        }

        @Override
        public void setBufferSize(int size) {
        }

        @Override
        public int getBufferSize() {
            return 0;
        }

        @Override
        public void flushBuffer() {
        }

        @Override
        public void resetBuffer() {
        }

        @Override
        public boolean isCommitted() {
            return false;
        }

        @Override
        public void reset() {
            renderParameters.clear();
            properties.clear();
        }

        @Override
        public java.io.OutputStream getPortletOutputStream() {
            return java.io.OutputStream.nullOutputStream();
        }

        @Override
        public PortletURL createRenderURL() {
            return null;
        }

        @Override
        public PortletURL createActionURL() {
            return null;
        }

        @Override
        public ResourceURL createResourceURL() {
            return null;
        }

        @Override
        public CacheControl getCacheControl() {
            return new RecordingCacheControl();
        }

        @Override
        public void addProperty(String key, String value) {
            properties.merge(key, value, (left, right) -> left + "," + right);
        }

        @Override
        public void setProperty(String key, String value) {
            properties.put(key, value);
        }

        @Override
        public String encodeURL(String path) {
            return path;
        }

        @Override
        public String getNamespace() {
            return "sample_";
        }

        @Override
        public void addProperty(javax.servlet.http.Cookie cookie) {
        }

        @Override
        public void addProperty(String key, org.w3c.dom.Element element) {
        }

        @Override
        public org.w3c.dom.Element createElement(String tagName) {
            return null;
        }

        @Override
        public void setTitle(String title) {
            this.title = title;
        }

        @Override
        public void setNextPossiblePortletModes(Collection<PortletMode> portletModes) {
        }

        @Override
        public void setLocale(Locale locale) {
            this.locale = locale;
        }

        @Override
        public void setCharacterEncoding(String characterEncoding) {
            this.characterEncoding = characterEncoding;
        }

        @Override
        public void setContentLength(int length) {
        }
    }

    private static class TestFilterConfig implements FilterConfig {
        private final String filterName;
        private final PortletContext context;
        private final Map<String, String> initParameters = Map.of("prefix", "filtered");

        TestFilterConfig(String filterName, PortletContext context) {
            this.filterName = filterName;
            this.context = context;
        }

        @Override
        public String getFilterName() {
            return filterName;
        }

        @Override
        public PortletContext getPortletContext() {
            return context;
        }

        @Override
        public String getInitParameter(String name) {
            return initParameters.get(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(initParameters.keySet());
        }
    }

    private static class TestPortletConfig implements PortletConfig {
        private final PortletContext context;
        private final Map<String, String> initParameters = Map.of("template", "/WEB-INF/view.jsp");

        TestPortletConfig(PortletContext context) {
            this.context = context;
        }

        @Override
        public String getPortletName() {
            return "sample-portlet";
        }

        @Override
        public PortletContext getPortletContext() {
            return context;
        }

        @Override
        public ResourceBundle getResourceBundle(Locale locale) {
            return new ResourceBundle() {
                @Override
                protected Object handleGetObject(String key) {
                    return "javax.portlet.title".equals(key) ? "Localized Sample Portlet" : null;
                }

                @Override
                public Enumeration<String> getKeys() {
                    return Collections.enumeration(List.of("javax.portlet.title"));
                }
            };
        }

        @Override
        public String getInitParameter(String name) {
            return initParameters.get(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(initParameters.keySet());
        }

        @Override
        public Enumeration<String> getPublicRenderParameterNames() {
            return Collections.enumeration(List.of("theme"));
        }

        @Override
        public String getDefaultNamespace() {
            return DEFAULT_NAMESPACE;
        }

        @Override
        public Enumeration<QName> getPublishingEventQNames() {
            return Collections.enumeration(List.of(new QName(DEFAULT_NAMESPACE, "published")));
        }

        @Override
        public Enumeration<QName> getProcessingEventQNames() {
            return Collections.enumeration(List.of(new QName(DEFAULT_NAMESPACE, "updated")));
        }

        @Override
        public Enumeration<Locale> getSupportedLocales() {
            return Collections.enumeration(List.of(Locale.ENGLISH, Locale.FRENCH));
        }

        @Override
        public Map<String, String[]> getContainerRuntimeOptions() {
            return Map.of("javax.portlet.escapeXml", new String[] {"true"});
        }
    }

    private static class RecordingPortletContext implements PortletContext {
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private final Map<String, String> initParameters = Map.of("application", "sample");
        private final List<String> includedTargets = new ArrayList<>();
        private final List<String> forwardedTargets = new ArrayList<>();

        @Override
        public String getServerInfo() {
            return "test-portal/2.0";
        }

        @Override
        public PortletRequestDispatcher getRequestDispatcher(String path) {
            return new RecordingPortletRequestDispatcher(path, includedTargets, forwardedTargets);
        }

        @Override
        public PortletRequestDispatcher getNamedDispatcher(String name) {
            return new RecordingPortletRequestDispatcher(name, includedTargets, forwardedTargets);
        }

        @Override
        public InputStream getResourceAsStream(String path) {
            return new ByteArrayInputStream(path.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public int getMajorVersion() {
            return 2;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public String getMimeType(String file) {
            return file.endsWith(".html") ? "text/html" : "application/octet-stream";
        }

        @Override
        public String getRealPath(String path) {
            return "/portal" + path;
        }

        @Override
        public Set<String> getResourcePaths(String path) {
            return Set.of(path + "/index.html");
        }

        @Override
        public URL getResource(String path) throws MalformedURLException {
            return URI.create("file:/portal" + path).toURL();
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
        public String getInitParameter(String name) {
            return initParameters.get(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(initParameters.keySet());
        }

        @Override
        public void log(String message) {
            attributes.put("lastLog", message);
        }

        @Override
        public void log(String message, Throwable throwable) {
            attributes.put("lastLog", message + ":" + throwable.getClass().getSimpleName());
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        @Override
        public void setAttribute(String name, Object object) {
            attributes.put(name, object);
        }

        @Override
        public String getPortletContextName() {
            return "sample-context";
        }

        @Override
        public Enumeration<String> getContainerRuntimeOptions() {
            return Collections.enumeration(List.of("javax.portlet.escapeXml"));
        }
    }

    private static class RecordingPortletRequestDispatcher implements PortletRequestDispatcher {
        private final String target;
        private final List<String> includedTargets;
        private final List<String> forwardedTargets;

        RecordingPortletRequestDispatcher(String target, List<String> includedTargets, List<String> forwardedTargets) {
            this.target = target;
            this.includedTargets = includedTargets;
            this.forwardedTargets = forwardedTargets;
        }

        @Override
        public void include(RenderRequest request, RenderResponse response) {
            includedTargets.add(target);
        }

        @Override
        public void include(PortletRequest request, PortletResponse response) {
            includedTargets.add(target);
        }

        @Override
        public void forward(PortletRequest request, PortletResponse response) {
            forwardedTargets.add(target);
        }
    }

    private static class TestPortalContext implements PortalContext {
        @Override
        public String getProperty(String name) {
            return "portal.vendor".equals(name) ? "metadata-tests" : null;
        }

        @Override
        public Enumeration<String> getPropertyNames() {
            return Collections.enumeration(List.of("portal.vendor"));
        }

        @Override
        public Enumeration<PortletMode> getSupportedPortletModes() {
            return Collections.enumeration(List.of(PortletMode.VIEW, PortletMode.EDIT, PortletMode.HELP));
        }

        @Override
        public Enumeration<WindowState> getSupportedWindowStates() {
            return Collections.enumeration(List.of(WindowState.NORMAL, WindowState.MAXIMIZED, WindowState.MINIMIZED));
        }

        @Override
        public String getPortalInfo() {
            return "metadata-test-portal/2.0";
        }
    }

    private static class RecordingPortletPreferences implements PortletPreferences {
        private final Map<String, String[]> values = new LinkedHashMap<>();

        RecordingPortletPreferences(Map<String, String> initialValues) {
            initialValues.forEach((key, value) -> values.put(key, new String[] {value}));
        }

        @Override
        public boolean isReadOnly(String key) {
            return "locked".equals(key);
        }

        @Override
        public String getValue(String key, String defaultValue) {
            String[] storedValues = values.get(key);
            return storedValues == null || storedValues.length == 0 ? defaultValue : storedValues[0];
        }

        @Override
        public String[] getValues(String key, String[] defaultValues) {
            return values.getOrDefault(key, defaultValues);
        }

        @Override
        public void setValue(String key, String value) throws ReadOnlyException {
            ensureWritable(key);
            values.put(key, new String[] {value});
        }

        @Override
        public void setValues(String key, String[] newValues) throws ReadOnlyException {
            ensureWritable(key);
            values.put(key, newValues);
        }

        @Override
        public Enumeration<String> getNames() {
            return Collections.enumeration(values.keySet());
        }

        @Override
        public Map<String, String[]> getMap() {
            return values;
        }

        @Override
        public void reset(String key) throws ReadOnlyException {
            ensureWritable(key);
            values.remove(key);
        }

        @Override
        public void store() {
        }

        private void ensureWritable(String key) throws ReadOnlyException {
            if (isReadOnly(key)) {
                throw new ReadOnlyException(key);
            }
        }
    }

    private static class RecordingPortletSession implements PortletSession {
        private final PortletContext context;
        private final Map<String, Object> applicationAttributes = new LinkedHashMap<>();
        private final Map<String, Object> portletAttributes = new LinkedHashMap<>();
        private final long creationTime = System.currentTimeMillis();
        private int maxInactiveInterval = 300;
        private boolean valid = true;

        RecordingPortletSession(PortletContext context) {
            this.context = context;
        }

        @Override
        public Object getAttribute(String name) {
            return getAttribute(name, PORTLET_SCOPE);
        }

        @Override
        public Object getAttribute(String name, int scope) {
            return attributes(scope).get(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return getAttributeNames(PORTLET_SCOPE);
        }

        @Override
        public Enumeration<String> getAttributeNames(int scope) {
            return Collections.enumeration(attributes(scope).keySet());
        }

        @Override
        public long getCreationTime() {
            return creationTime;
        }

        @Override
        public String getId() {
            return "session-1";
        }

        @Override
        public long getLastAccessedTime() {
            return creationTime;
        }

        @Override
        public int getMaxInactiveInterval() {
            return maxInactiveInterval;
        }

        @Override
        public void invalidate() {
            valid = false;
            applicationAttributes.clear();
            portletAttributes.clear();
        }

        @Override
        public boolean isNew() {
            return valid;
        }

        @Override
        public void removeAttribute(String name) {
            removeAttribute(name, PORTLET_SCOPE);
        }

        @Override
        public void removeAttribute(String name, int scope) {
            attributes(scope).remove(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            setAttribute(name, value, PORTLET_SCOPE);
        }

        @Override
        public void setAttribute(String name, Object value, int scope) {
            attributes(scope).put(name, value);
        }

        @Override
        public void setMaxInactiveInterval(int interval) {
            maxInactiveInterval = interval;
        }

        @Override
        public PortletContext getPortletContext() {
            return context;
        }

        @Override
        public Map<String, Object> getAttributeMap() {
            return portletAttributes;
        }

        @Override
        public Map<String, Object> getAttributeMap(int scope) {
            return attributes(scope);
        }

        private Map<String, Object> attributes(int scope) {
            return scope == APPLICATION_SCOPE ? applicationAttributes : portletAttributes;
        }
    }

    private static class RecordingBaseURL implements BaseURL {
        private final String basePath;
        private final Map<String, String[]> parameters = new LinkedHashMap<>();
        private final Map<String, String> properties = new LinkedHashMap<>();
        private boolean secure;

        RecordingBaseURL(String basePath) {
            this.basePath = basePath;
        }

        @Override
        public void setParameter(String name, String value) {
            parameters.put(name, new String[] {value});
        }

        @Override
        public void setParameter(String name, String[] values) {
            parameters.put(name, values);
        }

        @Override
        public void setParameters(Map<String, String[]> parameters) {
            this.parameters.clear();
            this.parameters.putAll(parameters);
        }

        @Override
        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return parameters;
        }

        @Override
        public void write(java.io.Writer out) throws IOException {
            out.write(toString());
        }

        @Override
        public void write(java.io.Writer out, boolean escapeXML) throws IOException {
            out.write(toString());
        }

        @Override
        public void addProperty(String key, String value) {
            properties.merge(key, value, (left, right) -> left + "," + right);
        }

        @Override
        public void setProperty(String key, String value) {
            properties.put(key, value);
        }

        @Override
        public String toString() {
            List<String> queryParts = new ArrayList<>();
            parameters.forEach((key, values) -> queryParts.add(key + "=" + String.join(",", values)));
            String query = queryParts.isEmpty() ? "" : "?" + String.join("&", queryParts);
            return basePath + query + ";secure=" + secure;
        }
    }

    private static class RecordingCacheControl implements CacheControl {
        private int expirationTime;
        private boolean publicScope;
        private String etag;
        private boolean useCachedContent;

        @Override
        public int getExpirationTime() {
            return expirationTime;
        }

        @Override
        public void setExpirationTime(int time) {
            expirationTime = time;
        }

        @Override
        public boolean isPublicScope() {
            return publicScope;
        }

        @Override
        public void setPublicScope(boolean publicScope) {
            this.publicScope = publicScope;
        }

        @Override
        public String getETag() {
            return etag;
        }

        @Override
        public void setETag(String etag) {
            this.etag = etag;
        }

        @Override
        public boolean useCachedContent() {
            return useCachedContent;
        }

        @Override
        public void setUseCachedContent(boolean useCachedContent) {
            this.useCachedContent = useCachedContent;
        }
    }
}
