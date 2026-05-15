/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.IWebApplication;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.IWebRequest;
import org.thymeleaf.web.IWebSession;

import static org.assertj.core.api.Assertions.assertThat;

public class WebEngineContextInnerRequestParameterValuesTest {

    @Test
    void processIteratesOverRequestParametersConvertedWithTypedToArray() {
        TemplateEngine templateEngine = new TemplateEngine();
        WebContext context = new WebContext(new TestWebExchange(Map.of("tags", new String[] {"spring", "native"})), Locale.US);
        context.setVariable("emptyStringArray", new String[0]);

        String output = templateEngine.process(
                "<ul><li th:each=\"tag : ${param.tags.toArray(emptyStringArray)}\" th:text=\"${tag}\"></li></ul>",
                context);

        assertThat(output).isEqualTo("<ul><li>spring</li><li>native</li></ul>");
    }

    private static final class TestWebExchange implements IWebExchange {

        private final IWebRequest request;
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
            return null;
        }

        @Override
        public IWebApplication getApplication() {
            return null;
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
            return "/";
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
            return Set.of();
        }

        @Override
        public Map<String, String[]> getHeaderMap() {
            return Map.of();
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
            return Set.of();
        }

        @Override
        public Map<String, String[]> getCookieMap() {
            return Map.of();
        }

        @Override
        public String[] getCookieValues(final String name) {
            return null;
        }
    }
}
