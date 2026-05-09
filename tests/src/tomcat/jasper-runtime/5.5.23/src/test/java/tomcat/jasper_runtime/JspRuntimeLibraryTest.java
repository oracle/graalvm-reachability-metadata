/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_runtime;

import org.apache.jasper.runtime.JspRuntimeLibrary;
import org.junit.jupiter.api.Test;

import java.beans.PropertyEditorSupport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

public class JspRuntimeLibraryTest {
    @Test
    void introspectHelperSetsSimpleBeanProperty() throws Exception {
        SampleBean bean = new SampleBean();

        JspRuntimeLibrary.introspecthelper(bean, "count", "42", null, null, false);

        assertThat(bean.getCount()).isEqualTo(42);
    }

    @Test
    void introspectHelperSetsStringArrayBeanPropertyFromServletRequest() throws Exception {
        SampleBean bean = new SampleBean();
        Map<String, String[]> parameters = new HashMap<>();
        parameters.put("tags", new String[] {"jsp", "tomcat"});
        ServletRequest request = new ParameterServletRequest(parameters);

        JspRuntimeLibrary.introspecthelper(bean, "tags", null, request, "tags", false);

        assertThat(bean.getTags()).containsExactly("jsp", "tomcat");
    }

    @Test
    void beanInfoPropertyEditorCreatesConvertedValue() throws Exception {
        Object value = JspRuntimeLibrary.getValueFromBeanInfoPropertyEditor(
                EditedValue.class, "value", "jasper", EditedValueEditor.class);

        assertThat(value).isInstanceOf(EditedValue.class);
        assertThat(((EditedValue) value).getText()).isEqualTo("JASPER");
    }

    public static class SampleBean {
        private int count;
        private String[] tags;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String[] getTags() {
            return tags;
        }

        public void setTags(String[] tags) {
            this.tags = tags;
        }
    }

    public static class EditedValue {
        private final String text;

        public EditedValue(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    public static class EditedValueEditor extends PropertyEditorSupport {
        @Override
        public void setAsText(String text) {
            setValue(new EditedValue(text.toUpperCase(Locale.ROOT)));
        }
    }

    private static class ParameterServletRequest implements ServletRequest {
        private final Map<String, String[]> parameters;

        ParameterServletRequest(Map<String, String[]> parameters) {
            this.parameters = parameters;
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public Enumeration getAttributeNames() {
            return Collections.enumeration(Collections.emptyList());
        }

        @Override
        public String getCharacterEncoding() {
            return null;
        }

        @Override
        public void setCharacterEncoding(String encoding) throws UnsupportedEncodingException {
        }

        @Override
        public int getContentLength() {
            return 0;
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
            String[] values = getParameterValues(name);
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
        }

        @Override
        public void removeAttribute(String name) {
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }

        @Override
        public Enumeration getLocales() {
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
            return 12345;
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
    }
}
