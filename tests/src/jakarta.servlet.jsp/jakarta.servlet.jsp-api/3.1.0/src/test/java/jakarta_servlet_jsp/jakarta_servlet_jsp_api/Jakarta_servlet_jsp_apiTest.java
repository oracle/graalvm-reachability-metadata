/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet_jsp.jakarta_servlet_jsp_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.FeatureDescriptor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.el.ELContext;
import jakarta.el.ELContextEvent;
import jakarta.el.ELContextListener;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.el.FunctionMapper;
import jakarta.el.VariableMapper;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.ErrorData;
import jakarta.servlet.jsp.HttpJspPage;
import jakarta.servlet.jsp.JspApplicationContext;
import jakarta.servlet.jsp.JspContext;
import jakarta.servlet.jsp.JspEngineInfo;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspFactory;
import jakarta.servlet.jsp.JspTagException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.SkipPageException;
import jakarta.servlet.jsp.el.ExpressionEvaluator;
import jakarta.servlet.jsp.el.VariableResolver;
import jakarta.servlet.jsp.tagext.BodyContent;
import jakarta.servlet.jsp.tagext.BodyTagSupport;
import jakarta.servlet.jsp.tagext.DynamicAttributes;
import jakarta.servlet.jsp.tagext.FunctionInfo;
import jakarta.servlet.jsp.tagext.IterationTag;
import jakarta.servlet.jsp.tagext.JspFragment;
import jakarta.servlet.jsp.tagext.JspIdConsumer;
import jakarta.servlet.jsp.tagext.PageData;
import jakarta.servlet.jsp.tagext.SimpleTagSupport;
import jakarta.servlet.jsp.tagext.Tag;
import jakarta.servlet.jsp.tagext.TagAdapter;
import jakarta.servlet.jsp.tagext.TagAttributeInfo;
import jakarta.servlet.jsp.tagext.TagData;
import jakarta.servlet.jsp.tagext.TagExtraInfo;
import jakarta.servlet.jsp.tagext.TagLibraryValidator;
import jakarta.servlet.jsp.tagext.TagSupport;
import jakarta.servlet.jsp.tagext.TryCatchFinally;
import jakarta.servlet.jsp.tagext.ValidationMessage;
import jakarta.servlet.jsp.tagext.VariableInfo;
import org.junit.jupiter.api.Test;

public class Jakarta_servlet_jsp_apiTest {
    @Test
    void errorDataExceptionsAndEngineInfoExposeState() {
        IllegalStateException rootCause = new IllegalStateException("boom");
        ErrorData errorData = new ErrorData(rootCause, 500, "/broken.jsp", "jspServlet");
        JspEngineInfo engineInfo = new TestJspEngineInfo("3.1");

        assertThat(errorData.getThrowable()).isSameAs(rootCause);
        assertThat(errorData.getStatusCode()).isEqualTo(500);
        assertThat(errorData.getRequestURI()).isEqualTo("/broken.jsp");
        assertThat(errorData.getServletName()).isEqualTo("jspServlet");
        assertThat(engineInfo.getSpecificationVersion()).isEqualTo("3.1");

        assertThat(new JspException("message")).hasMessage("message");
        assertThat(new JspException("message", rootCause)).hasMessage("message").hasCause(rootCause);
        assertThat(new JspException(rootCause)).hasCause(rootCause);
        assertThat(new JspTagException("tag message")).hasMessage("tag message");
        assertThat(new JspTagException("tag message", rootCause)).hasMessage("tag message").hasCause(rootCause);
        assertThat(new SkipPageException("skip message")).hasMessage("skip message");
        assertThat(new SkipPageException("skip message", rootCause)).hasMessage("skip message").hasCause(rootCause);
    }

    @Test
    void jspFactoryDefaultFactoryDelegatesToCustomImplementation() {
        JspFactory originalFactory = JspFactory.getDefaultFactory();
        TestJspFactory factory = new TestJspFactory();
        TestPageContext pageContext = new TestPageContext();
        factory.setPageContext(pageContext);

        try {
            JspFactory.setDefaultFactory(factory);

            PageContext returnedPageContext = JspFactory.getDefaultFactory()
                    .getPageContext(null, null, null, "/error.jsp", true, 128, false);
            JspApplicationContext applicationContext = factory.getJspApplicationContext(null);
            TestELResolver resolver = new TestELResolver();
            TestELContextListener listener = new TestELContextListener();

            applicationContext.addELResolver(resolver);
            applicationContext.addELContextListener(listener);
            factory.releasePageContext(returnedPageContext);

            assertThat(returnedPageContext).isSameAs(pageContext);
            assertThat(factory.getEngineInfo().getSpecificationVersion()).isEqualTo("3.1");
            assertThat(factory.releasedPageContext).isSameAs(pageContext);
            assertThat(factory.applicationContext.resolvers).containsExactly(resolver);
            assertThat(factory.applicationContext.listeners).containsExactly(listener);
            assertThat(applicationContext.getExpressionFactory()).isNull();
        } finally {
            JspFactory.setDefaultFactory(originalFactory);
        }
    }

    @Test
    void jspContextStoresFindsEnumeratesAndRemovesScopedAttributes() {
        TestPageContext pageContext = new TestPageContext();

        pageContext.setAttribute("applicationName", "catalog", PageContext.APPLICATION_SCOPE);
        pageContext.setAttribute("requestName", "request", PageContext.REQUEST_SCOPE);
        pageContext.setAttribute("sessionName", "session", PageContext.SESSION_SCOPE);
        pageContext.setAttribute("pageName", "page");

        assertThat(pageContext.getAttribute("pageName")).isEqualTo("page");
        assertThat(pageContext.getAttribute("requestName", PageContext.REQUEST_SCOPE)).isEqualTo("request");
        assertThat(pageContext.findAttribute("applicationName")).isEqualTo("catalog");
        assertThat(pageContext.getAttributesScope("sessionName")).isEqualTo(PageContext.SESSION_SCOPE);
        assertThat(Collections.list(pageContext.getAttributeNamesInScope(PageContext.PAGE_SCOPE)))
                .containsExactly("pageName");

        pageContext.removeAttribute("pageName", PageContext.PAGE_SCOPE);
        pageContext.removeAttribute("requestName");

        assertThat(pageContext.getAttribute("pageName")).isNull();
        assertThat(pageContext.findAttribute("requestName")).isNull();
        assertThat(pageContext.getAttributesScope("missing")).isZero();
    }

    @Test
    void pageContextExposesPageObjectsAndErrorData() {
        TestPageContext pageContext = new TestPageContext();
        TestJspWriter writer = new TestJspWriter(32, true);
        ServletException exception = new ServletException("page failed");
        pageContext.setOut(writer);
        pageContext.setException(exception);

        assertThat(pageContext.getOut()).isSameAs(writer);
        assertThat(pageContext.getPage()).isSameAs(pageContext);
        assertThat(pageContext.getELContext()).isNotNull();
        assertThat(pageContext.getErrorData().getThrowable()).isSameAs(exception);
        assertThat(pageContext.getErrorData().getStatusCode()).isEqualTo(500);
        assertThat(pageContext.getErrorData().getRequestURI()).isEqualTo("/test.jsp");
        assertThat(pageContext.getErrorData().getServletName()).isEqualTo("testServlet");
    }

    @Test
    void jspWriterWritesClearsFlushesAndReportsBufferState() throws IOException {
        TestJspWriter writer = new TestJspWriter(64, false);

        writer.print(true);
        writer.print(' ');
        writer.print(42);
        writer.print(' ');
        writer.print(3.5d);
        writer.newLine();
        writer.println("done");
        writer.write(new char[] {'o', 'k'}, 0, 2);
        writer.flush();

        assertThat(writer.getBufferSize()).isEqualTo(64);
        assertThat(writer.isAutoFlush()).isFalse();
        assertThat(writer.getRemaining()).isEqualTo(64 - writer.contents().length());
        assertThat(writer.contents()).isEqualTo("true 42 3.5\ndone\nok");
        assertThat(writer.flushed).isTrue();

        writer.clearBuffer();
        assertThat(writer.contents()).isEmpty();
        writer.print("temporary");
        writer.clear();
        assertThat(writer.contents()).isEmpty();
        writer.close();
        assertThat(writer.closed).isTrue();
    }

    @Test
    void bodyContentBuffersTextAndWritesToAnotherWriter() throws IOException {
        TestJspWriter enclosingWriter = new TestJspWriter(JspWriter.DEFAULT_BUFFER, true);
        TestBodyContent bodyContent = new TestBodyContent(enclosingWriter);
        StringWriter copy = new StringWriter();

        bodyContent.print("Hello");
        bodyContent.print(' ');
        bodyContent.println("body");
        bodyContent.writeOut(copy);

        assertThat(bodyContent.getEnclosingWriter()).isSameAs(enclosingWriter);
        assertThat(bodyContent.getString()).isEqualTo("Hello body\n");
        assertThat(copy).hasToString("Hello body\n");
        assertThat(readAll(bodyContent.getReader())).isEqualTo("Hello body\n");

        bodyContent.clearBody();
        assertThat(bodyContent.getString()).isEmpty();
    }

    @Test
    void tagSupportAndBodyTagSupportManageLifecycle() throws JspException {
        TestPageContext pageContext = new TestPageContext();
        TagSupport parent = new TagSupport();
        TagSupport child = new TagSupport();
        TestBodyContent bodyContent = new TestBodyContent(new TestJspWriter(32, true));
        BodyTagSupport bodyTag = new BodyTagSupport();

        parent.setId("parentTag");
        child.setPageContext(pageContext);
        child.setParent(parent);
        bodyTag.setPageContext(pageContext);
        bodyTag.setParent(parent);
        bodyTag.setBodyContent(bodyContent);
        bodyTag.doInitBody();

        assertThat(child.getId()).isNull();
        assertThat(parent.getId()).isEqualTo("parentTag");
        assertThat(child.getParent()).isSameAs(parent);
        assertThat(TagSupport.findAncestorWithClass(child, TagSupport.class)).isSameAs(parent);
        assertThat(child.doStartTag()).isEqualTo(Tag.SKIP_BODY);
        assertThat(child.doAfterBody()).isEqualTo(IterationTag.SKIP_BODY);
        assertThat(child.doEndTag()).isEqualTo(Tag.EVAL_PAGE);
        assertThat(bodyTag.getBodyContent()).isSameAs(bodyContent);
        assertThat(bodyTag.getPreviousOut()).isSameAs(bodyContent.getEnclosingWriter());
        assertThat(bodyTag.doAfterBody()).isEqualTo(IterationTag.SKIP_BODY);
    }

    @Test
    void simpleTagsFragmentsAdaptersAndDynamicAttributesWorkTogether() throws JspException, IOException {
        TestPageContext pageContext = new TestPageContext();
        TestSimpleTag parent = new TestSimpleTag();
        TestSimpleTag child = new TestSimpleTag();
        TestJspFragment fragment = new TestJspFragment(pageContext, "fragment output");
        StringWriter output = new StringWriter();

        parent.setJspContext(pageContext);
        child.setJspContext(pageContext);
        child.setParent(parent);
        child.setJspBody(fragment);
        child.setJspId("generated-id");
        child.setDynamicAttribute("urn:test", "answer", 42);
        fragment.invoke(output);
        child.doCatch(new IllegalArgumentException("handled"));
        child.doFinally();
        TagAdapter adapter = new TagAdapter(child);

        assertThat(child.getAssignedJspContext()).isSameAs(pageContext);
        assertThat(child.getParent()).isSameAs(parent);
        assertThat(SimpleTagSupport.findAncestorWithClass(child, TestSimpleTag.class)).isSameAs(parent);
        assertThat(fragment.getJspContext()).isSameAs(pageContext);
        assertThat(output).hasToString("fragment output");
        assertThat(child.jspId).isEqualTo("generated-id");
        assertThat(child.dynamicAttributes).containsEntry("urn:test:answer", 42);
        assertThat(child.caught).isInstanceOf(IllegalArgumentException.class);
        assertThat(child.finallyCalled).isTrue();
        assertThat(adapter.getAdaptee()).isSameAs(child);
    }

    @Test
    void tagMetadataDescriptorsExposeConstructorValues() {
        FunctionInfo functionInfo = new FunctionInfo(
                "escape", "example.Functions", "java.lang.String escape(java.lang.String)");
        VariableInfo variableInfo = new VariableInfo("item", "java.lang.String", true, VariableInfo.AT_BEGIN);
        TagAttributeInfo idAttribute = new TagAttributeInfo("id", true, "java.lang.String", false);
        TagAttributeInfo runtimeAttribute = new TagAttributeInfo("value", false, "java.lang.Object", true);
        TagData tagData = new TagData(new Object[][] {
                {"id", "customerTag"},
                {"value", TagData.REQUEST_TIME_VALUE}
        });
        ValidationMessage message = new ValidationMessage("node-1", "Invalid tag body");
        TagExtraInfo tagExtraInfo = new TestTagExtraInfo();
        TagLibraryValidator validator = new TestTagLibraryValidator();

        validator.setInitParameters(Map.of("mode", "strict"));
        tagData.setAttribute("extra", "metadata");

        assertThat(functionInfo.getName()).isEqualTo("escape");
        assertThat(functionInfo.getFunctionClass()).isEqualTo("example.Functions");
        assertThat(functionInfo.getFunctionSignature()).contains("escape");
        assertThat(variableInfo.getVarName()).isEqualTo("item");
        assertThat(variableInfo.getClassName()).isEqualTo("java.lang.String");
        assertThat(variableInfo.getDeclare()).isTrue();
        assertThat(variableInfo.getScope()).isEqualTo(VariableInfo.AT_BEGIN);
        assertThat(idAttribute.getName()).isEqualTo("id");
        assertThat(idAttribute.isRequired()).isTrue();
        assertThat(idAttribute.getTypeName()).isEqualTo("java.lang.String");
        assertThat(idAttribute.canBeRequestTime()).isFalse();
        assertThat(idAttribute.isFragment()).isFalse();
        assertThat(TagAttributeInfo.getIdAttribute(new TagAttributeInfo[] {runtimeAttribute, idAttribute}))
                .isSameAs(idAttribute);
        assertThat(tagData.getId()).isEqualTo("customerTag");
        assertThat(tagData.getAttribute("value")).isSameAs(TagData.REQUEST_TIME_VALUE);
        assertThat(tagData.getAttributeString("extra")).isEqualTo("metadata");
        assertThat(message.getId()).isEqualTo("node-1");
        assertThat(message.getMessage()).isEqualTo("Invalid tag body");
        assertThat(tagExtraInfo.getVariableInfo(tagData)).isEmpty();
        assertThat(tagExtraInfo.isValid(tagData)).isTrue();
        assertThat(tagExtraInfo.validate(tagData)).isNull();
        assertThat(validator.getInitParameters()).containsEntry("mode", "strict");
        assertThat(validator.validate("test", "urn:test", new StringPageData("<jsp:root />"))).isNull();
    }

    @Test
    void httpJspPageLifecycleCallsArePlainServletEntryPoints() throws ServletException, IOException {
        TestHttpJspPage page = new TestHttpJspPage();

        page.jspInit();
        page._jspService(null, null);
        page.jspDestroy();

        assertThat(page.initialized).isTrue();
        assertThat(page.serviceCalls).isEqualTo(1);
        assertThat(page.destroyed).isTrue();
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[32];
        int read = reader.read(buffer);
        while (read != -1) {
            builder.append(buffer, 0, read);
            read = reader.read(buffer);
        }
        return builder.toString();
    }

    private static final class TestJspEngineInfo extends JspEngineInfo {
        private final String specificationVersion;

        private TestJspEngineInfo(String specificationVersion) {
            this.specificationVersion = specificationVersion;
        }

        @Override
        public String getSpecificationVersion() {
            return specificationVersion;
        }
    }

    private static final class TestJspFactory extends JspFactory {
        private final TestJspApplicationContext applicationContext = new TestJspApplicationContext();
        private PageContext pageContext;
        private PageContext releasedPageContext;

        private void setPageContext(PageContext pageContext) {
            this.pageContext = pageContext;
        }

        @Override
        public PageContext getPageContext(Servlet servlet, ServletRequest request, ServletResponse response,
                String errorPageUrl, boolean needsSession, int bufferSize, boolean autoFlush) {
            return pageContext;
        }

        @Override
        public void releasePageContext(PageContext pageContext) {
            releasedPageContext = pageContext;
        }

        @Override
        public JspEngineInfo getEngineInfo() {
            return new TestJspEngineInfo("3.1");
        }

        @Override
        public JspApplicationContext getJspApplicationContext(ServletContext context) {
            return applicationContext;
        }
    }

    private static final class TestJspApplicationContext implements JspApplicationContext {
        private final List<ELResolver> resolvers = new ArrayList<>();
        private final List<ELContextListener> listeners = new ArrayList<>();

        @Override
        public void addELResolver(ELResolver resolver) {
            resolvers.add(resolver);
        }

        @Override
        public ExpressionFactory getExpressionFactory() {
            return null;
        }

        @Override
        public void addELContextListener(ELContextListener listener) {
            listeners.add(listener);
        }
    }

    private static final class TestELResolver extends ELResolver {
        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            return null;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            return null;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            return true;
        }

        @Override
        public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
            return Collections.emptyIterator();
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return Object.class;
        }
    }

    private static final class TestELContextListener implements ELContextListener {
        @Override
        public void contextCreated(ELContextEvent event) {
        }
    }

    private static final class TestPageContext extends PageContext {
        private final Map<Integer, Map<String, Object>> scopedAttributes = new LinkedHashMap<>();
        private final ELContext elContext = new ELContext() {
            @Override
            public ELResolver getELResolver() {
                return new TestELResolver();
            }

            @Override
            public FunctionMapper getFunctionMapper() {
                return null;
            }

            @Override
            public VariableMapper getVariableMapper() {
                return null;
            }
        };
        private JspWriter out;
        private Throwable exception;

        private TestPageContext() {
            scopedAttributes.put(PAGE_SCOPE, new LinkedHashMap<>());
            scopedAttributes.put(REQUEST_SCOPE, new LinkedHashMap<>());
            scopedAttributes.put(SESSION_SCOPE, new LinkedHashMap<>());
            scopedAttributes.put(APPLICATION_SCOPE, new LinkedHashMap<>());
        }

        private void setOut(JspWriter out) {
            this.out = out;
        }

        private void setException(Throwable exception) {
            this.exception = exception;
        }

        @Override
        public void setAttribute(String name, Object value) {
            setAttribute(name, value, PAGE_SCOPE);
        }

        @Override
        public void setAttribute(String name, Object value, int scope) {
            scopedAttributes.get(scope).put(name, value);
        }

        @Override
        public Object getAttribute(String name) {
            return getAttribute(name, PAGE_SCOPE);
        }

        @Override
        public Object getAttribute(String name, int scope) {
            return scopedAttributes.get(scope).get(name);
        }

        @Override
        public Object findAttribute(String name) {
            for (int scope : scopedAttributes.keySet()) {
                Object value = getAttribute(name, scope);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        @Override
        public void removeAttribute(String name) {
            scopedAttributes.values().forEach(attributes -> attributes.remove(name));
        }

        @Override
        public void removeAttribute(String name, int scope) {
            scopedAttributes.get(scope).remove(name);
        }

        @Override
        public int getAttributesScope(String name) {
            for (Map.Entry<Integer, Map<String, Object>> entry : scopedAttributes.entrySet()) {
                if (entry.getValue().containsKey(name)) {
                    return entry.getKey();
                }
            }
            return 0;
        }

        @Override
        public Enumeration<String> getAttributeNamesInScope(int scope) {
            return Collections.enumeration(scopedAttributes.get(scope).keySet());
        }

        @Override
        public JspWriter getOut() {
            return out;
        }

        @Override
        @SuppressWarnings("deprecation")
        public ExpressionEvaluator getExpressionEvaluator() {
            return null;
        }

        @Override
        @SuppressWarnings("deprecation")
        public VariableResolver getVariableResolver() {
            return null;
        }

        @Override
        public ELContext getELContext() {
            return elContext;
        }

        @Override
        public void initialize(Servlet servlet, ServletRequest request, ServletResponse response, String errorPageUrl,
                boolean needsSession, int bufferSize, boolean autoFlush) {
        }

        @Override
        public void release() {
            scopedAttributes.values().forEach(Map::clear);
        }

        @Override
        public HttpSession getSession() {
            return null;
        }

        @Override
        public Object getPage() {
            return this;
        }

        @Override
        public ServletRequest getRequest() {
            return null;
        }

        @Override
        public ServletResponse getResponse() {
            return null;
        }

        @Override
        public Exception getException() {
            return exception instanceof Exception pageException ? pageException : null;
        }

        @Override
        public ServletConfig getServletConfig() {
            return null;
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public void forward(String relativeUrlPath) {
        }

        @Override
        public void include(String relativeUrlPath) {
        }

        @Override
        public void include(String relativeUrlPath, boolean flush) {
        }

        @Override
        public void handlePageException(Exception exception) {
            this.exception = exception;
        }

        @Override
        public void handlePageException(Throwable throwable) {
            this.exception = throwable;
        }

        @Override
        public ErrorData getErrorData() {
            return new ErrorData(exception, 500, "/test.jsp", "testServlet");
        }
    }

    private static class TestJspWriter extends JspWriter {
        private final StringBuilder buffer = new StringBuilder();
        private boolean flushed;
        private boolean closed;

        TestJspWriter(int bufferSize, boolean autoFlush) {
            super(bufferSize, autoFlush);
        }

        private String contents() {
            return buffer.toString();
        }

        @Override
        public void newLine() {
            buffer.append('\n');
        }

        @Override
        public void print(boolean value) {
            buffer.append(value);
        }

        @Override
        public void print(char value) {
            buffer.append(value);
        }

        @Override
        public void print(int value) {
            buffer.append(value);
        }

        @Override
        public void print(long value) {
            buffer.append(value);
        }

        @Override
        public void print(float value) {
            buffer.append(value);
        }

        @Override
        public void print(double value) {
            buffer.append(value);
        }

        @Override
        public void print(char[] value) {
            buffer.append(value);
        }

        @Override
        public void print(String value) {
            buffer.append(value == null ? "null" : value);
        }

        @Override
        public void print(Object value) {
            buffer.append(value);
        }

        @Override
        public void println() {
            newLine();
        }

        @Override
        public void println(boolean value) {
            print(value);
            newLine();
        }

        @Override
        public void println(char value) {
            print(value);
            newLine();
        }

        @Override
        public void println(int value) {
            print(value);
            newLine();
        }

        @Override
        public void println(long value) {
            print(value);
            newLine();
        }

        @Override
        public void println(float value) {
            print(value);
            newLine();
        }

        @Override
        public void println(double value) {
            print(value);
            newLine();
        }

        @Override
        public void println(char[] value) {
            print(value);
            newLine();
        }

        @Override
        public void println(String value) {
            print(value);
            newLine();
        }

        @Override
        public void println(Object value) {
            print(value);
            newLine();
        }

        @Override
        public void clear() {
            buffer.setLength(0);
        }

        @Override
        public void clearBuffer() {
            clear();
        }

        @Override
        public void flush() {
            flushed = true;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public int getRemaining() {
            if (bufferSize < 0) {
                return bufferSize;
            }
            return bufferSize - buffer.length();
        }

        @Override
        public void write(char[] chars, int offset, int length) {
            buffer.append(chars, offset, length);
        }
    }

    private static final class TestBodyContent extends BodyContent {
        private final StringBuilder content = new StringBuilder();

        private TestBodyContent(JspWriter enclosingWriter) {
            super(enclosingWriter);
        }

        @Override
        public Reader getReader() {
            return new StringReader(content.toString());
        }

        @Override
        public String getString() {
            return content.toString();
        }

        @Override
        public void writeOut(Writer writer) throws IOException {
            writer.write(content.toString());
        }

        @Override
        public void newLine() {
            content.append('\n');
        }

        @Override
        public void print(boolean value) {
            content.append(value);
        }

        @Override
        public void print(char value) {
            content.append(value);
        }

        @Override
        public void print(int value) {
            content.append(value);
        }

        @Override
        public void print(long value) {
            content.append(value);
        }

        @Override
        public void print(float value) {
            content.append(value);
        }

        @Override
        public void print(double value) {
            content.append(value);
        }

        @Override
        public void print(char[] value) {
            content.append(value);
        }

        @Override
        public void print(String value) {
            content.append(value == null ? "null" : value);
        }

        @Override
        public void print(Object value) {
            content.append(value);
        }

        @Override
        public void println() {
            newLine();
        }

        @Override
        public void println(boolean value) {
            print(value);
            newLine();
        }

        @Override
        public void println(char value) {
            print(value);
            newLine();
        }

        @Override
        public void println(int value) {
            print(value);
            newLine();
        }

        @Override
        public void println(long value) {
            print(value);
            newLine();
        }

        @Override
        public void println(float value) {
            print(value);
            newLine();
        }

        @Override
        public void println(double value) {
            print(value);
            newLine();
        }

        @Override
        public void println(char[] value) {
            print(value);
            newLine();
        }

        @Override
        public void println(String value) {
            print(value);
            newLine();
        }

        @Override
        public void println(Object value) {
            print(value);
            newLine();
        }

        @Override
        public void clear() {
            content.setLength(0);
        }

        @Override
        public void clearBuffer() {
            clear();
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public int getRemaining() {
            return JspWriter.UNBOUNDED_BUFFER;
        }

        @Override
        public void write(char[] chars, int offset, int length) {
            content.append(chars, offset, length);
        }
    }

    private static final class TestJspFragment extends JspFragment {
        private final JspContext jspContext;
        private final String text;

        private TestJspFragment(JspContext jspContext, String text) {
            this.jspContext = jspContext;
            this.text = text;
        }

        @Override
        public JspContext getJspContext() {
            return jspContext;
        }

        @Override
        public void invoke(Writer writer) throws IOException {
            writer.write(text);
        }
    }

    private static final class TestSimpleTag extends SimpleTagSupport
            implements DynamicAttributes, JspIdConsumer, TryCatchFinally {
        private final Map<String, Object> dynamicAttributes = new HashMap<>();
        private String jspId;
        private Throwable caught;
        private boolean finallyCalled;

        private JspContext getAssignedJspContext() {
            return getJspContext();
        }

        @Override
        public void setDynamicAttribute(String uri, String localName, Object value) {
            dynamicAttributes.put(uri + ":" + localName, value);
        }

        @Override
        public void setJspId(String jspId) {
            this.jspId = jspId;
        }

        @Override
        public void doCatch(Throwable throwable) {
            caught = throwable;
        }

        @Override
        public void doFinally() {
            finallyCalled = true;
        }
    }

    private static final class TestTagExtraInfo extends TagExtraInfo {
    }

    private static final class TestTagLibraryValidator extends TagLibraryValidator {
        @Override
        public ValidationMessage[] validate(String prefix, String uri, PageData page) {
            return null;
        }
    }

    private static final class StringPageData extends PageData {
        private final String content;

        private StringPageData(String content) {
            this.content = content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class TestHttpJspPage implements HttpJspPage {
        private boolean initialized;
        private boolean destroyed;
        private int serviceCalls;

        @Override
        public void init(ServletConfig config) {
            jspInit();
        }

        @Override
        public ServletConfig getServletConfig() {
            return null;
        }

        @Override
        public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
            _jspService((HttpServletRequest) request, (HttpServletResponse) response);
        }

        @Override
        public String getServletInfo() {
            return "test JSP page";
        }

        @Override
        public void destroy() {
            jspDestroy();
        }

        @Override
        public void jspInit() {
            initialized = true;
        }

        @Override
        public void jspDestroy() {
            destroyed = true;
        }

        @Override
        public void _jspService(HttpServletRequest request, HttpServletResponse response) {
            serviceCalls++;
        }
    }
}
