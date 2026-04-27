/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.io.InputStream;
import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebEngineContext;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.web.IWebApplication;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.IWebRequest;
import org.thymeleaf.web.IWebSession;

import static org.assertj.core.api.Assertions.assertThat;

public class WebEngineContextRequestParameterValuesTest {

    @Test
    void requestParameterValuesToArrayExpandsTargetArrayToParameterLength() {
        TemplateEngine templateEngine = new TemplateEngine();
        IEngineConfiguration configuration = templateEngine.getConfiguration();
        Map<String, String[]> parameters = new LinkedHashMap<>();
        parameters.put("tag", new String[] {"alpha", "beta"});

        WebEngineContext context = new WebEngineContext(
                configuration,
                new TemplateData("test-template", null, null, TemplateMode.HTML, null),
                Collections.emptyMap(),
                new TestWebExchange(parameters),
                Locale.US,
                Collections.emptyMap());

        Map<String, Object> requestParameters = requestParameters(context);
        List<String> values = requestParameterValues(requestParameters, "tag");

        String[] copy = values.toArray(new String[0]);

        assertThat(copy).containsExactly("alpha", "beta");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requestParameters(final WebEngineContext context) {
        return (Map<String, Object>) context.getVariable("param");
    }

    @SuppressWarnings("unchecked")
    private static List<String> requestParameterValues(final Map<String, Object> requestParameters, final String name) {
        return (List<String>) requestParameters.get(name);
    }

    private static final class TestWebExchange implements IWebExchange {

        private final IWebRequest request;
        private final IWebSession session = new EmptyWebSession();
        private final IWebApplication application = new EmptyWebApplication();
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private TestWebExchange(final Map<String, String[]> parameters) {
            this.request = new TestWebRequest(parameters);
        }

        @Override
        public IWebRequest getRequest() {
            return this.request;
        }

        @Override
        public IWebSession getSession() {
            return this.session;
        }

        @Override
        public IWebApplication getApplication() {
            return this.application;
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public Locale getLocale() {
            return Locale.US;
        }

        @Override
        public String getContentType() {
            return "text/html";
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public boolean containsAttribute(final String name) {
            return this.attributes.containsKey(name);
        }

        @Override
        public int getAttributeCount() {
            return this.attributes.size();
        }

        @Override
        public Set<String> getAllAttributeNames() {
            return this.attributes.keySet();
        }

        @Override
        public Map<String, Object> getAttributeMap() {
            return this.attributes;
        }

        @Override
        public Object getAttributeValue(final String name) {
            return this.attributes.get(name);
        }

        @Override
        public void setAttributeValue(final String name, final Object value) {
            this.attributes.put(name, value);
        }

        @Override
        public void removeAttribute(final String name) {
            this.attributes.remove(name);
        }

        @Override
        public String transformURL(final String url) {
            return url;
        }
    }

    private static final class TestWebRequest implements IWebRequest {

        private final Map<String, String[]> parameters;

        private TestWebRequest(final Map<String, String[]> parameters) {
            this.parameters = parameters;
        }

        @Override
        public String getMethod() {
            return "GET";
        }

        @Override
        public String getScheme() {
            return "https";
        }

        @Override
        public String getServerName() {
            return "example.org";
        }

        @Override
        public Integer getServerPort() {
            return 443;
        }

        @Override
        public String getApplicationPath() {
            return "";
        }

        @Override
        public String getPathWithinApplication() {
            return "/test";
        }

        @Override
        public String getQueryString() {
            return null;
        }

        @Override
        public boolean containsHeader(final String name) {
            return false;
        }

        @Override
        public int getHeaderCount() {
            return 0;
        }

        @Override
        public Set<String> getAllHeaderNames() {
            return Collections.emptySet();
        }

        @Override
        public Map<String, String[]> getHeaderMap() {
            return Collections.emptyMap();
        }

        @Override
        public String[] getHeaderValues(final String name) {
            return null;
        }

        @Override
        public boolean containsParameter(final String name) {
            return this.parameters.containsKey(name);
        }

        @Override
        public int getParameterCount() {
            return this.parameters.size();
        }

        @Override
        public Set<String> getAllParameterNames() {
            return this.parameters.keySet();
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return this.parameters;
        }

        @Override
        public String[] getParameterValues(final String name) {
            return this.parameters.get(name);
        }

        @Override
        public boolean containsCookie(final String name) {
            return false;
        }

        @Override
        public int getCookieCount() {
            return 0;
        }

        @Override
        public Set<String> getAllCookieNames() {
            return Collections.emptySet();
        }

        @Override
        public Map<String, String[]> getCookieMap() {
            return Collections.emptyMap();
        }

        @Override
        public String[] getCookieValues(final String name) {
            return null;
        }
    }

    private static final class EmptyWebSession implements IWebSession {

        private final Map<String, Object> attributes = new LinkedHashMap<>();

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean containsAttribute(final String name) {
            return this.attributes.containsKey(name);
        }

        @Override
        public int getAttributeCount() {
            return this.attributes.size();
        }

        @Override
        public Set<String> getAllAttributeNames() {
            return this.attributes.keySet();
        }

        @Override
        public Map<String, Object> getAttributeMap() {
            return this.attributes;
        }

        @Override
        public Object getAttributeValue(final String name) {
            return this.attributes.get(name);
        }

        @Override
        public void setAttributeValue(final String name, final Object value) {
            this.attributes.put(name, value);
        }

        @Override
        public void removeAttribute(final String name) {
            this.attributes.remove(name);
        }
    }

    private static final class EmptyWebApplication implements IWebApplication {

        private final Map<String, Object> attributes = new LinkedHashMap<>();

        @Override
        public boolean containsAttribute(final String name) {
            return this.attributes.containsKey(name);
        }

        @Override
        public int getAttributeCount() {
            return this.attributes.size();
        }

        @Override
        public Set<String> getAllAttributeNames() {
            return this.attributes.keySet();
        }

        @Override
        public Map<String, Object> getAttributeMap() {
            return this.attributes;
        }

        @Override
        public Object getAttributeValue(final String name) {
            return this.attributes.get(name);
        }

        @Override
        public void setAttributeValue(final String name, final Object value) {
            this.attributes.put(name, value);
        }

        @Override
        public void removeAttribute(final String name) {
            this.attributes.remove(name);
        }

        @Override
        public boolean resourceExists(final String path) {
            return false;
        }

        @Override
        public InputStream getResourceAsStream(final String path) {
            return null;
        }
    }
}
