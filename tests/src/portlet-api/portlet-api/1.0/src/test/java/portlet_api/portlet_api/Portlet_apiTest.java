/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package portlet_api.portlet_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
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
import javax.portlet.GenericPortlet;
import javax.portlet.PortalContext;
import javax.portlet.PortletConfig;
import javax.portlet.PortletContext;
import javax.portlet.PortletException;
import javax.portlet.PortletMode;
import javax.portlet.PortletModeException;
import javax.portlet.PortletPreferences;
import javax.portlet.PortletRequest;
import javax.portlet.PortletRequestDispatcher;
import javax.portlet.PortletSecurityException;
import javax.portlet.PortletSession;
import javax.portlet.PortletSessionUtil;
import javax.portlet.PortletURL;
import javax.portlet.PreferencesValidator;
import javax.portlet.ReadOnlyException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.UnavailableException;
import javax.portlet.UnmodifiableException;
import javax.portlet.ValidatorException;
import javax.portlet.WindowState;
import javax.portlet.WindowStateException;

import org.junit.jupiter.api.Test;

public class Portlet_apiTest {

    @Test
    void portletModesAreLowercaseValueObjectsWithPredefinedConstants() {
        PortletMode mixedCaseView = new PortletMode("VIEW");
        PortletMode customMode = new PortletMode("CONFIGURE");

        assertThat(mixedCaseView).isEqualTo(PortletMode.VIEW);
        assertThat(mixedCaseView.hashCode()).isEqualTo(PortletMode.VIEW.hashCode());
        assertThat(mixedCaseView.toString()).isEqualTo("view");
        assertThat(PortletMode.EDIT.toString()).isEqualTo("edit");
        assertThat(PortletMode.HELP.toString()).isEqualTo("help");
        assertThat(customMode.toString()).isEqualTo("configure");
        assertThat(customMode).isEqualTo(new PortletMode("configure"));
        assertThat(customMode).isNotEqualTo(PortletMode.VIEW);
        assertThat(customMode).isNotEqualTo("configure");
        assertThatThrownBy(() -> new PortletMode(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PortletMode name can not be NULL");
    }

    @Test
    void windowStatesAreLowercaseValueObjectsWithPredefinedConstants() {
        WindowState mixedCaseMaximized = new WindowState("MAXIMIZED");
        WindowState customState = new WindowState("DETACHED");

        assertThat(mixedCaseMaximized).isEqualTo(WindowState.MAXIMIZED);
        assertThat(mixedCaseMaximized.hashCode()).isEqualTo(WindowState.MAXIMIZED.hashCode());
        assertThat(mixedCaseMaximized.toString()).isEqualTo("maximized");
        assertThat(WindowState.NORMAL.toString()).isEqualTo("normal");
        assertThat(WindowState.MINIMIZED.toString()).isEqualTo("minimized");
        assertThat(customState.toString()).isEqualTo("detached");
        assertThat(customState).isEqualTo(new WindowState("detached"));
        assertThat(customState).isNotEqualTo(WindowState.NORMAL);
        assertThat(customState).isNotEqualTo("detached");
        assertThatThrownBy(() -> new WindowState(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WindowState name can not be NULL");
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
    void publicConstantsExposeSpecifiedNamesAndScopes() {
        assertThat(PortletRequest.USER_INFO).isEqualTo("javax.portlet.userinfo");
        assertThat(PortletRequest.BASIC_AUTH).isEqualTo("BASIC");
        assertThat(PortletRequest.FORM_AUTH).isEqualTo("FORM");
        assertThat(PortletRequest.CLIENT_CERT_AUTH).isEqualTo("CLIENT_CERT");
        assertThat(PortletRequest.DIGEST_AUTH).isEqualTo("DIGEST");
        assertThat(RenderResponse.EXPIRATION_CACHE).isEqualTo("portlet.expiration-cache");
        assertThat(PortletSession.APPLICATION_SCOPE).isEqualTo(1);
        assertThat(PortletSession.PORTLET_SCOPE).isEqualTo(2);
    }

    @Test
    void portletExceptionsPreserveMessagesCausesAndStackTraces() {
        IllegalStateException cause = new IllegalStateException("nested failure");

        PortletException messageOnly = new PortletException("top level");
        PortletException withCause = new PortletException("wrapped", cause);
        PortletException causeOnly = new PortletException(cause);
        PortletException empty = new PortletException();

        assertThat(messageOnly).hasMessage("top level");
        assertThat(messageOnly.getCause()).isNull();
        assertThat(withCause).hasMessage("wrapped");
        assertThat(withCause.getCause()).isSameAs(cause);
        assertThat(causeOnly.getCause()).isSameAs(cause);
        assertThat(empty.getCause()).isNull();

        StringWriter stackTrace = new StringWriter();
        withCause.printStackTrace(new PrintWriter(stackTrace));

        assertThat(stackTrace.toString())
                .contains("javax.portlet.PortletException: wrapped")
                .contains("Nested Exception is")
                .contains("java.lang.IllegalStateException: nested failure");
    }

    @Test
    void specializedExceptionsExposeUnavailableTimeInvalidStateModeAndFailedKeys() {
        Throwable cause = new IllegalArgumentException("invalid");
        WindowState detached = new WindowState("detached");
        PortletMode configure = new PortletMode("configure");

        UnavailableException permanent = new UnavailableException("maintenance");
        UnavailableException temporary = new UnavailableException("retry", 30);
        UnavailableException immediateRetry = new UnavailableException("retry", 0);
        WindowStateException stateException = new WindowStateException("unsupported state", cause, detached);
        PortletModeException modeException = new PortletModeException("unsupported mode", cause, configure);
        ValidatorException validatorException = new ValidatorException("failed", cause, List.of("theme", "layout"));

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
        assertThat(new UnmodifiableException("unmodifiable")).hasMessage("unmodifiable");
        assertThat(new PortletSecurityException("security")).hasMessage("security");
    }

    @Test
    void genericPortletDelegatesConfigurationAndDispatchesRenderModes() throws Exception {
        RecordingPortletContext context = new RecordingPortletContext();
        TestPortletConfig config = new TestPortletConfig(context);
        RecordingGenericPortlet portlet = new RecordingGenericPortlet();

        portlet.init(config);

        assertThat(portlet.events).containsExactly("init");
        assertThat(portlet.getPortletConfig()).isSameAs(config);
        assertThat(portlet.getPortletName()).isEqualTo("sample-portlet");
        assertThat(portlet.getPortletContext()).isSameAs(context);
        assertThat(portlet.getResourceBundle(Locale.ENGLISH).getString("javax.portlet.title"))
                .isEqualTo("Localized Sample Portlet");
        assertThat(portlet.getInitParameter("template")).isEqualTo("/WEB-INF/view.jsp");
        assertThat(Collections.list(portlet.getInitParameterNames())).containsExactly("template");

        assertRenderModeDispatchesTo(portlet, PortletMode.VIEW, "view");
        assertRenderModeDispatchesTo(portlet, PortletMode.EDIT, "edit");
        assertRenderModeDispatchesTo(portlet, PortletMode.HELP, "help");

        RecordingRenderResponse minimizedResponse = new RecordingRenderResponse();
        int eventCountBeforeMinimizedRender = portlet.events.size();
        portlet.render(new TestRenderRequest(WindowState.MINIMIZED, PortletMode.VIEW), minimizedResponse);

        assertThat(minimizedResponse.title).isEqualTo("Localized Sample Portlet");
        assertThat(portlet.events).hasSize(eventCountBeforeMinimizedRender);

        PortletException unknownMode = assertThrows(PortletException.class, () ->
                portlet.render(new TestRenderRequest(WindowState.NORMAL, new PortletMode("preview")),
                        new RecordingRenderResponse()));
        assertThat(unknownMode).hasMessage("unknown portlet mode: preview");
    }

    @Test
    void genericPortletDefaultMethodsReportUnimplementedActionsAndViews() {
        GenericPortlet portlet = new GenericPortlet() {
        };

        PortletException actionException = assertThrows(PortletException.class, () ->
                portlet.processAction(new TestActionRequest(), new RecordingActionResponse()));
        PortletException viewException = assertThrows(PortletException.class, () -> {
            portlet.init(new TestPortletConfig(new RecordingPortletContext()));
            portlet.render(new TestRenderRequest(WindowState.NORMAL, PortletMode.VIEW),
                    new RecordingRenderResponse());
        });

        assertThat(actionException).hasMessage("processAction method not implemented");
        assertThat(viewException).hasMessage("doView method not implemented");
    }

    @Test
    void responseUrlPreferencesAndContextCollaboratorsCanBeImplementedThroughPublicInterfaces() throws Exception {
        RecordingPortletContext context = new RecordingPortletContext();
        RecordingActionResponse actionResponse = new RecordingActionResponse();
        RecordingPortletPreferences preferences = new RecordingPortletPreferences(Map.of("theme", "light"));
        TestPortletURL actionUrl = new TestPortletURL("/action");

        context.setAttribute("counter", 1);
        actionResponse.setWindowState(WindowState.MAXIMIZED);
        actionResponse.setPortletMode(PortletMode.EDIT);
        actionResponse.setRenderParameter("tab", "settings");
        actionResponse.setRenderParameters(Map.of("theme", new String[] {"dark"}));
        actionResponse.sendRedirect("/done");
        actionUrl.setWindowState(WindowState.NORMAL);
        actionUrl.setPortletMode(PortletMode.HELP);
        actionUrl.setParameter("item", "42");
        actionUrl.setSecure(true);
        preferences.setValue("theme", "dark");
        preferences.setValues("columns", new String[] {"name", "status"});
        preferences.store();

        assertThat(context.getAttribute("counter")).isEqualTo(1);
        assertThat(Collections.list(context.getAttributeNames())).containsExactly("counter");
        assertThat(actionResponse.windowState).isEqualTo(WindowState.MAXIMIZED);
        assertThat(actionResponse.portletMode).isEqualTo(PortletMode.EDIT);
        assertThat(actionResponse.redirectLocation).isEqualTo("/done");
        assertThat(actionResponse.renderParameters.get("theme")).containsExactly("dark");
        assertThat(actionUrl.toString()).isEqualTo("/action?item=42;state=normal;mode=help;secure=true");
        assertThat(preferences.getValue("theme", "default")).isEqualTo("dark");
        assertThat(preferences.getValues("columns", new String[] {"missing"})).containsExactly("name", "status");
        assertThat(Collections.list(preferences.getNames())).containsExactly("theme", "columns");
        assertThat(preferences.getMap()).containsKeys("theme", "columns");
    }

    @Test
    void preferencesValidatorAcceptsValidPreferencesAndReportsInvalidKeys() throws Exception {
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

        preferences.setValues("columns", new String[] {"name", "status"});
        validator.validate(preferences);

        preferences.setValue("theme", "contrast");
        preferences.reset("columns");
        ValidatorException exception = assertThrows(ValidatorException.class, () -> validator.validate(preferences));

        assertThat(Collections.list(exception.getFailedKeys())).containsExactly("theme", "columns");
    }

    @Test
    void portletContextExposesResourcesDispatchersAndDiagnosticsThroughPublicInterface() throws Exception {
        RecordingPortletContext context = new RecordingPortletContext();
        RecordingRenderResponse response = new RecordingRenderResponse();
        RenderRequest request = new TestRenderRequest(WindowState.NORMAL, PortletMode.VIEW);

        context.getRequestDispatcher("/WEB-INF/view.jsp").include(request, response);
        context.getNamedDispatcher("header").include(request, response);
        context.log("rendered");
        context.log("failed", new IllegalStateException("boom"));

        assertThat(new String(context.getResourceAsStream("/index.html").readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("/index.html");
        assertThat(context.getMimeType("/index.html")).isEqualTo("text/html");
        assertThat(context.getMimeType("/assets/app.bin")).isEqualTo("application/octet-stream");
        assertThat(context.getRealPath("/index.html")).isEqualTo("/portal/index.html");
        assertThat(context.getResource("/index.html")).isEqualTo(new URL("file", "", "/portal/index.html"));
        assertThat(context.getResourcePaths("/public")).containsExactly("/public/index.html");
        assertThat(context.getPortletContextName()).isEqualTo("sample-context");
        assertThat(context.getInitParameter("application")).isEqualTo("sample");
        assertThat(Collections.list(context.getInitParameterNames())).containsExactly("application");
        assertThat(response.writer.toString()).isEqualTo("included:/WEB-INF/view.jspincluded:header");
        assertThat(context.getAttribute("lastLog")).isEqualTo("failed:IllegalStateException");
    }

    private static void assertRenderModeDispatchesTo(RecordingGenericPortlet portlet, PortletMode mode,
            String expectedEvent) throws Exception {
        RecordingRenderResponse response = new RecordingRenderResponse();

        portlet.render(new TestRenderRequest(WindowState.NORMAL, mode), response);

        assertThat(response.title).isEqualTo("Localized Sample Portlet");
        assertThat(response.writer.toString()).isEqualTo(expectedEvent);
        assertThat(portlet.events).contains(expectedEvent);
    }

    private static class RecordingGenericPortlet extends GenericPortlet {
        private final List<String> events = new ArrayList<>();

        @Override
        public void init() {
            events.add("init");
        }

        @Override
        protected void doView(RenderRequest request, RenderResponse response) throws IOException {
            events.add("view");
            response.getWriter().write("view");
        }

        @Override
        protected void doEdit(RenderRequest request, RenderResponse response) throws IOException {
            events.add("edit");
            response.getWriter().write("edit");
        }

        @Override
        protected void doHelp(RenderRequest request, RenderResponse response) throws IOException {
            events.add("help");
            response.getWriter().write("help");
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
        public Enumeration getInitParameterNames() {
            return Collections.enumeration(initParameters.keySet());
        }
    }

    private static class RecordingPortletContext implements PortletContext {
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private final Map<String, String> initParameters = Map.of("application", "sample");

        @Override
        public String getServerInfo() {
            return "test-portal/1.0";
        }

        @Override
        public PortletRequestDispatcher getRequestDispatcher(String path) {
            return new RecordingPortletRequestDispatcher(path);
        }

        @Override
        public PortletRequestDispatcher getNamedDispatcher(String name) {
            return new RecordingPortletRequestDispatcher(name);
        }

        @Override
        public InputStream getResourceAsStream(String path) {
            return new ByteArrayInputStream(path.getBytes());
        }

        @Override
        public int getMajorVersion() {
            return 1;
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
        public Set getResourcePaths(String path) {
            return Set.of(path + "/index.html");
        }

        @Override
        public URL getResource(String path) throws MalformedURLException {
            return new URL("file", "", "/portal" + path);
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public Enumeration getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        @Override
        public String getInitParameter(String name) {
            return initParameters.get(name);
        }

        @Override
        public Enumeration getInitParameterNames() {
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
    }

    private static class RecordingPortletRequestDispatcher implements PortletRequestDispatcher {
        private final String target;

        RecordingPortletRequestDispatcher(String target) {
            this.target = target;
        }

        @Override
        public void include(RenderRequest request, RenderResponse response) throws IOException {
            response.getWriter().write("included:" + target);
        }
    }

    private static class TestRenderRequest extends BasePortletRequest implements RenderRequest {
        TestRenderRequest(WindowState windowState, PortletMode portletMode) {
            super(windowState, portletMode);
        }
    }

    private static class TestActionRequest extends BasePortletRequest implements ActionRequest {
        TestActionRequest() {
            super(WindowState.NORMAL, PortletMode.VIEW);
        }

        @Override
        public InputStream getPortletInputStream() {
            return new ByteArrayInputStream("name=value".getBytes());
        }

        @Override
        public void setCharacterEncoding(String encoding) throws UnsupportedEncodingException {
            if (!"UTF-8".equals(encoding)) {
                throw new UnsupportedEncodingException(encoding);
            }
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new StringReader("name=value"));
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public String getContentType() {
            return "application/x-www-form-urlencoded";
        }

        @Override
        public int getContentLength() {
            return "name=value".length();
        }
    }

    private abstract static class BasePortletRequest implements PortletRequest {
        private final WindowState windowState;
        private final PortletMode portletMode;
        private final Map<String, Object> attributes = new HashMap<>();
        private final Map<String, String[]> parameters = new LinkedHashMap<>();
        private final PortletPreferences preferences = new RecordingPortletPreferences(Map.of("theme", "light"));

        BasePortletRequest(WindowState windowState, PortletMode portletMode) {
            this.windowState = windowState;
            this.portletMode = portletMode;
            parameters.put("page", new String[] {"1"});
        }

        @Override
        public boolean isWindowStateAllowed(WindowState state) {
            return !WindowState.MINIMIZED.equals(state);
        }

        @Override
        public boolean isPortletModeAllowed(PortletMode mode) {
            return PortletMode.VIEW.equals(mode) || PortletMode.EDIT.equals(mode) || PortletMode.HELP.equals(mode);
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
        public PortletPreferences getPreferences() {
            return preferences;
        }

        @Override
        public PortletSession getPortletSession() {
            return getPortletSession(true);
        }

        @Override
        public PortletSession getPortletSession(boolean create) {
            return create ? new RecordingPortletSession(new RecordingPortletContext()) : null;
        }

        @Override
        public String getProperty(String name) {
            return "X-Test".equals(name) ? "enabled" : null;
        }

        @Override
        public Enumeration getProperties(String name) {
            return Collections.enumeration(getProperty(name) == null ? List.of() : List.of(getProperty(name)));
        }

        @Override
        public Enumeration getPropertyNames() {
            return Collections.enumeration(List.of("X-Test"));
        }

        @Override
        public PortalContext getPortalContext() {
            return new TestPortalContext();
        }

        @Override
        public String getAuthType() {
            return BASIC_AUTH;
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
            return "user".equals(role);
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public Enumeration getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        @Override
        public String getParameter(String name) {
            String[] values = parameters.get(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override
        public Enumeration getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return parameters.get(name);
        }

        @Override
        public Map getParameterMap() {
            return parameters;
        }

        @Override
        public boolean isSecure() {
            return true;
        }

        @Override
        public void setAttribute(String name, Object object) {
            attributes.put(name, object);
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
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
        public Enumeration getResponseContentTypes() {
            return Collections.enumeration(List.of("text/html", "text/plain"));
        }

        @Override
        public Locale getLocale() {
            return Locale.ENGLISH;
        }

        @Override
        public Enumeration getLocales() {
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
    }

    private static class TestPortalContext implements PortalContext {
        @Override
        public String getProperty(String name) {
            return "portal.vendor".equals(name) ? "metadata-tests" : null;
        }

        @Override
        public Enumeration getPropertyNames() {
            return Collections.enumeration(List.of("portal.vendor"));
        }

        @Override
        public Enumeration getSupportedPortletModes() {
            return Collections.enumeration(List.of(PortletMode.VIEW, PortletMode.EDIT, PortletMode.HELP));
        }

        @Override
        public Enumeration getSupportedWindowStates() {
            return Collections.enumeration(List.of(WindowState.NORMAL, WindowState.MAXIMIZED, WindowState.MINIMIZED));
        }

        @Override
        public String getPortalInfo() {
            return "metadata-test-portal/1.0";
        }
    }

    private static class RecordingRenderResponse implements RenderResponse {
        private final StringWriter writer = new StringWriter();
        private final Map<String, String> properties = new LinkedHashMap<>();
        private String title;
        private String contentType = "text/html";
        private int bufferSize = 8192;
        private boolean committed;

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public PortletURL createRenderURL() {
            return new TestPortletURL("/render");
        }

        @Override
        public PortletURL createActionURL() {
            return new TestPortletURL("/action");
        }

        @Override
        public String getNamespace() {
            return "sample_";
        }

        @Override
        public void setTitle(String title) {
            this.title = title;
        }

        @Override
        public void setContentType(String type) {
            contentType = type;
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public PrintWriter getWriter() {
            return new PrintWriter(writer, true);
        }

        @Override
        public Locale getLocale() {
            return Locale.ENGLISH;
        }

        @Override
        public void setBufferSize(int size) {
            bufferSize = size;
        }

        @Override
        public int getBufferSize() {
            return bufferSize;
        }

        @Override
        public void flushBuffer() {
            committed = true;
        }

        @Override
        public void resetBuffer() {
            writer.getBuffer().setLength(0);
        }

        @Override
        public boolean isCommitted() {
            return committed;
        }

        @Override
        public void reset() {
            resetBuffer();
            properties.clear();
        }

        @Override
        public OutputStream getPortletOutputStream() {
            return OutputStream.nullOutputStream();
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
            return path + ";encoded=true";
        }
    }

    private static class RecordingActionResponse implements ActionResponse {
        private final Map<String, String[]> renderParameters = new LinkedHashMap<>();
        private final Map<String, String> properties = new LinkedHashMap<>();
        private WindowState windowState;
        private PortletMode portletMode;
        private String redirectLocation;

        @Override
        public void setWindowState(WindowState state) {
            windowState = state;
        }

        @Override
        public void setPortletMode(PortletMode mode) {
            portletMode = mode;
        }

        @Override
        public void sendRedirect(String location) {
            redirectLocation = location;
        }

        @Override
        public void setRenderParameters(Map parameters) {
            renderParameters.clear();
            for (Object entryObject : parameters.entrySet()) {
                Map.Entry entry = (Map.Entry) entryObject;
                renderParameters.put(String.valueOf(entry.getKey()), (String[]) entry.getValue());
            }
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
        public void addProperty(String key, String value) {
            properties.merge(key, value, (left, right) -> left + "," + right);
        }

        @Override
        public void setProperty(String key, String value) {
            properties.put(key, value);
        }

        @Override
        public String encodeURL(String path) {
            return path + ";encoded=true";
        }
    }

    private static class TestPortletURL implements PortletURL {
        private final String basePath;
        private final Map<String, String[]> parameters = new LinkedHashMap<>();
        private WindowState windowState;
        private PortletMode portletMode;
        private boolean secure;

        TestPortletURL(String basePath) {
            this.basePath = basePath;
        }

        @Override
        public void setWindowState(WindowState state) throws WindowStateException {
            if (state == null) {
                throw new WindowStateException("missing state", state);
            }
            windowState = state;
        }

        @Override
        public void setPortletMode(PortletMode mode) throws PortletModeException {
            if (mode == null) {
                throw new PortletModeException("missing mode", mode);
            }
            portletMode = mode;
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
        public void setParameters(Map parameters) {
            this.parameters.clear();
            for (Object entryObject : parameters.entrySet()) {
                Map.Entry entry = (Map.Entry) entryObject;
                this.parameters.put(String.valueOf(entry.getKey()), (String[]) entry.getValue());
            }
        }

        @Override
        public void setSecure(boolean secure) throws PortletSecurityException {
            this.secure = secure;
        }

        @Override
        public String toString() {
            List<String> queryParts = new ArrayList<>();
            parameters.forEach((key, values) -> queryParts.add(key + "=" + values[0]));
            String query = queryParts.isEmpty() ? "" : "?" + String.join("&", queryParts);
            return basePath + query + ";state=" + windowState + ";mode=" + portletMode + ";secure=" + secure;
        }
    }

    private static class RecordingPortletPreferences implements PortletPreferences {
        private final Map<String, String[]> values = new LinkedHashMap<>();
        private boolean stored;

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
        public Enumeration getNames() {
            return Collections.enumeration(values.keySet());
        }

        @Override
        public Map getMap() {
            return values;
        }

        @Override
        public void reset(String key) throws ReadOnlyException {
            ensureWritable(key);
            values.remove(key);
        }

        @Override
        public void store() {
            stored = true;
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
        public Enumeration getAttributeNames() {
            return getAttributeNames(PORTLET_SCOPE);
        }

        @Override
        public Enumeration getAttributeNames(int scope) {
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

        private Map<String, Object> attributes(int scope) {
            return scope == APPLICATION_SCOPE ? applicationAttributes : portletAttributes;
        }
    }
}
