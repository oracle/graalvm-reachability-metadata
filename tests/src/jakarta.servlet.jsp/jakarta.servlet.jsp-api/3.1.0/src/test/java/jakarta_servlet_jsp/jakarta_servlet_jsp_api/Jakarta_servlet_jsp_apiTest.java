/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet_jsp.jakarta_servlet_jsp_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.el.ELContext;
import jakarta.el.ELContextListener;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.ErrorData;
import jakarta.servlet.jsp.JspApplicationContext;
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
import jakarta.servlet.jsp.tagext.BodyTag;
import jakarta.servlet.jsp.tagext.BodyTagSupport;
import jakarta.servlet.jsp.tagext.FunctionInfo;
import jakarta.servlet.jsp.tagext.IterationTag;
import jakarta.servlet.jsp.tagext.JspTag;
import jakarta.servlet.jsp.tagext.SimpleTagSupport;
import jakarta.servlet.jsp.tagext.Tag;
import jakarta.servlet.jsp.tagext.TagAdapter;
import jakarta.servlet.jsp.tagext.TagAttributeInfo;
import jakarta.servlet.jsp.tagext.TagData;
import jakarta.servlet.jsp.tagext.TagExtraInfo;
import jakarta.servlet.jsp.tagext.TagFileInfo;
import jakarta.servlet.jsp.tagext.TagInfo;
import jakarta.servlet.jsp.tagext.TagLibraryInfo;
import jakarta.servlet.jsp.tagext.TagLibraryValidator;
import jakarta.servlet.jsp.tagext.TagSupport;
import jakarta.servlet.jsp.tagext.TagVariableInfo;
import jakarta.servlet.jsp.tagext.ValidationMessage;
import jakarta.servlet.jsp.tagext.VariableInfo;
import org.junit.jupiter.api.Test;

public class Jakarta_servlet_jsp_apiTest {
    @Test
    void exceptionTypesPreserveMessagesAndCauses() {
        IllegalStateException cause = new IllegalStateException("root");

        JspException jspException = new JspException("jsp failed", cause);
        JspTagException tagException = new JspTagException("tag failed", cause);
        SkipPageException skipPageException = new SkipPageException("skip page", cause);

        assertThat(jspException)
                .hasMessage("jsp failed")
                .hasCause(cause);
        assertThat(tagException)
                .isInstanceOf(JspException.class)
                .hasMessage("tag failed")
                .hasCause(cause);
        assertThat(skipPageException)
                .isInstanceOf(JspException.class)
                .hasMessage("skip page")
                .hasCause(cause);
        assertThat(new JspException(cause)).hasCause(cause);
    }

    @Test
    void errorDataExposesPageErrorState() {
        RuntimeException throwable = new RuntimeException("boom");

        ErrorData errorData = new ErrorData(throwable, 503, "/broken.jsp", "jspServlet");

        assertThat(errorData.getThrowable()).isSameAs(throwable);
        assertThat(errorData.getStatusCode()).isEqualTo(503);
        assertThat(errorData.getRequestURI()).isEqualTo("/broken.jsp");
        assertThat(errorData.getServletName()).isEqualTo("jspServlet");
    }

    @Test
    void pageContextBuildsErrorDataFromServletErrorAttributes() {
        RuntimeException throwable = new RuntimeException("boom");
        ErrorAttributesRequest request = new ErrorAttributesRequest();
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, throwable);
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 503);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/broken.jsp");
        request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME, "jspServlet");

        ErrorData errorData = new ErrorPageContext(request).getErrorData();

        assertThat(errorData.getThrowable()).isSameAs(throwable);
        assertThat(errorData.getStatusCode()).isEqualTo(503);
        assertThat(errorData.getRequestURI()).isEqualTo("/broken.jsp");
        assertThat(errorData.getServletName()).isEqualTo("jspServlet");
    }

    @Test
    void jspFactoryRegistersDefaultFactoryAndExposesApplicationContext() {
        JspFactory previousFactory = JspFactory.getDefaultFactory();
        RecordingJspFactory factory = new RecordingJspFactory();
        RecordingElResolver resolver = new RecordingElResolver();
        ELContextListener listener = event -> {
            throw new AssertionError("Registered listener should not be invoked by this test");
        };

        try {
            JspFactory.setDefaultFactory(factory);

            assertThat(JspFactory.getDefaultFactory()).isSameAs(factory);
            assertThat(factory.getEngineInfo().getSpecificationVersion()).isEqualTo("test-specification");

            JspApplicationContext applicationContext = factory.getJspApplicationContext(null);
            applicationContext.addELResolver(resolver);
            applicationContext.addELContextListener(listener);

            assertThat(factory.getApplicationContext().getResolvers()).containsExactly(resolver);
            assertThat(factory.getApplicationContext().getListeners()).containsExactly(listener);
            assertThat(applicationContext.getExpressionFactory()).isNull();

            assertThat(factory.getPageContext(null, null, null, "/errors/failure.jsp", true, 256, true)).isNull();
            assertThat(factory.getLastErrorPageUrl()).isEqualTo("/errors/failure.jsp");
            assertThat(factory.isLastSessionRequired()).isTrue();
            assertThat(factory.getLastBufferSize()).isEqualTo(256);
            assertThat(factory.isLastAutoFlush()).isTrue();

            factory.releasePageContext(null);
            assertThat(factory.getReleasedPageContexts()).containsExactly((PageContext) null);
        } finally {
            JspFactory.setDefaultFactory(previousFactory);
        }
    }

    @Test
    void tagDataStoresAttributesFromArraysAndHashtables() {
        Object[][] values = {
                {"id", "greetingTag"},
                {"literal", "hello"},
                {"runtime", TagData.REQUEST_TIME_VALUE}
        };
        TagData data = new TagData(values);

        assertThat(data.getId()).isEqualTo("greetingTag");
        assertThat(data.getAttributeString("literal")).isEqualTo("hello");
        assertThat(data.getAttribute("runtime")).isSameAs(TagData.REQUEST_TIME_VALUE);
        assertThat(Collections.list(data.getAttributes()))
                .containsExactlyInAnyOrder("id", "literal", "runtime");

        data.setAttribute("literal", "updated");
        assertThat(data.getAttributeString("literal")).isEqualTo("updated");

        Hashtable<String, Object> attributes = new Hashtable<>();
        attributes.put("id", "fromTable");
        attributes.put("enabled", Boolean.TRUE);
        TagData tableBackedData = new TagData(attributes);

        assertThat(tableBackedData.getId()).isEqualTo("fromTable");
        assertThat(tableBackedData.getAttribute("enabled")).isEqualTo(Boolean.TRUE);
        assertThat(Collections.list(tableBackedData.getAttributes()))
                .containsExactlyInAnyOrder("id", "enabled");
    }

    @Test
    void tagMetadataValueObjectsExposeConstructorState() {
        TagAttributeInfo id = new TagAttributeInfo(TagAttributeInfo.ID, true, "java.lang.String", false);
        TagAttributeInfo action = new TagAttributeInfo(
                "action",
                false,
                "jakarta.el.MethodExpression",
                true,
                true,
                "Action callback",
                true,
                true,
                "java.lang.String",
                "void invoke()");
        VariableInfo variable = new VariableInfo("item", "java.lang.String", true, VariableInfo.AT_BEGIN);
        TagVariableInfo tagVariable = new TagVariableInfo(
                "explicitName", "nameAttribute", "java.lang.Integer", false, VariableInfo.NESTED);
        FunctionInfo function = new FunctionInfo("length", "example.Functions", "int length(java.lang.String)");
        ValidationMessage message = new ValidationMessage("field", "must be present");

        assertThat(TagAttributeInfo.getIdAttribute(new TagAttributeInfo[] {action, id})).isSameAs(id);
        assertThat(action.getName()).isEqualTo("action");
        assertThat(action.isRequired()).isFalse();
        assertThat(action.getTypeName()).isEqualTo("jakarta.el.MethodExpression");
        assertThat(action.canBeRequestTime()).isTrue();
        assertThat(action.isFragment()).isTrue();
        assertThat(action.getDescription()).isEqualTo("Action callback");
        assertThat(action.isDeferredValue()).isTrue();
        assertThat(action.isDeferredMethod()).isTrue();
        assertThat(action.getExpectedTypeName()).isEqualTo("java.lang.String");
        assertThat(action.getMethodSignature()).isEqualTo("void invoke()");
        assertThat(action.toString()).contains("action");

        assertThat(variable.getVarName()).isEqualTo("item");
        assertThat(variable.getClassName()).isEqualTo("java.lang.String");
        assertThat(variable.getDeclare()).isTrue();
        assertThat(variable.getScope()).isEqualTo(VariableInfo.AT_BEGIN);
        assertThat(tagVariable.getNameGiven()).isEqualTo("explicitName");
        assertThat(tagVariable.getNameFromAttribute()).isEqualTo("nameAttribute");
        assertThat(tagVariable.getClassName()).isEqualTo("java.lang.Integer");
        assertThat(tagVariable.getDeclare()).isFalse();
        assertThat(tagVariable.getScope()).isEqualTo(VariableInfo.NESTED);
        assertThat(function.getName()).isEqualTo("length");
        assertThat(function.getFunctionClass()).isEqualTo("example.Functions");
        assertThat(function.getFunctionSignature()).isEqualTo("int length(java.lang.String)");
        assertThat(message.getId()).isEqualTo("field");
        assertThat(message.getMessage()).isEqualTo("must be present");
    }

    @Test
    void tagInfoDelegatesToTagExtraInfoAndStoresMetadata() {
        VariableInfo[] variables = {new VariableInfo("result", "java.lang.String", true, VariableInfo.AT_END)};
        RecordingTagExtraInfo tagExtraInfo = new RecordingTagExtraInfo(false, variables);
        TagAttributeInfo[] attributes = {
                new TagAttributeInfo("name", true, "java.lang.String", true, false)
        };
        TagVariableInfo[] tagVariables = {
                new TagVariableInfo("var", null, "java.lang.String", true, VariableInfo.AT_END)
        };
        TagInfo tagInfo = new TagInfo(
                "hello",
                "example.HelloTag",
                TagInfo.BODY_CONTENT_SCRIPTLESS,
                "Renders a greeting",
                null,
                tagExtraInfo,
                attributes,
                "Hello tag",
                "small.png",
                "large.png",
                tagVariables,
                true);
        TagData tagData = new TagData(new Object[][] {{"id", "hello1"}});

        assertThat(tagExtraInfo.getTagInfo()).isSameAs(tagInfo);
        assertThat(tagInfo.getTagName()).isEqualTo("hello");
        assertThat(tagInfo.getTagClassName()).isEqualTo("example.HelloTag");
        assertThat(tagInfo.getBodyContent()).isEqualTo(TagInfo.BODY_CONTENT_SCRIPTLESS);
        assertThat(tagInfo.getInfoString()).isEqualTo("Renders a greeting");
        assertThat(tagInfo.getAttributes()).containsExactly(attributes);
        assertThat(tagInfo.getDisplayName()).isEqualTo("Hello tag");
        assertThat(tagInfo.getSmallIcon()).isEqualTo("small.png");
        assertThat(tagInfo.getLargeIcon()).isEqualTo("large.png");
        assertThat(tagInfo.getTagVariableInfos()).containsExactly(tagVariables);
        assertThat(tagInfo.hasDynamicAttributes()).isTrue();
        assertThat(tagInfo.getVariableInfo(tagData)).containsExactly(variables);
        assertThat(tagInfo.isValid(tagData)).isFalse();
        assertThat(tagInfo.validate(tagData))
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.getId()).isEqualTo("hello1");
                    assertThat(message.getMessage()).isEqualTo("isValid() == false");
                });
    }

    @Test
    void tagLibraryInfoFindsTagsFilesFunctionsAndNestedLibraries() {
        FunctionInfo function = new FunctionInfo("escape", "example.Functions", "java.lang.String escape(java.lang.String)");
        TagInfo tagInfo = new TagInfo(
                "format", "example.FormatTag", TagInfo.BODY_CONTENT_EMPTY, "Formats values", null, null,
                new TagAttributeInfo[0]);
        TagFileInfo tagFileInfo = new TagFileInfo("panel", "/WEB-INF/tags/panel.tag", tagInfo);
        TestTagLibraryInfo nested = new TestTagLibraryInfo("nested", "urn:nested");
        TestTagLibraryInfo library = new TestTagLibraryInfo("ui", "urn:ui");
        library.configure(new TagInfo[] {tagInfo}, new TagFileInfo[] {tagFileInfo}, new FunctionInfo[] {function},
                new TagLibraryInfo[] {nested});

        tagInfo.setTagLibrary(library);

        assertThat(library.getPrefixString()).isEqualTo("ui");
        assertThat(library.getURI()).isEqualTo("urn:ui");
        assertThat(library.getShortName()).isEqualTo("ui-tags");
        assertThat(library.getReliableURN()).isEqualTo("urn:ui:reliable");
        assertThat(library.getRequiredVersion()).isEqualTo("3.1");
        assertThat(library.getInfoString()).isEqualTo("Test tag library");
        assertThat(library.getTags()).containsExactly(tagInfo);
        assertThat(library.getTag("format")).isSameAs(tagInfo);
        assertThat(library.getTag("missing")).isNull();
        assertThat(library.getTagFiles()).containsExactly(tagFileInfo);
        assertThat(library.getTagFile("panel")).isSameAs(tagFileInfo);
        assertThat(tagFileInfo.getName()).isEqualTo("panel");
        assertThat(tagFileInfo.getPath()).isEqualTo("/WEB-INF/tags/panel.tag");
        assertThat(tagFileInfo.getTagInfo()).isSameAs(tagInfo);
        assertThat(library.getFunctions()).containsExactly(function);
        assertThat(library.getFunction("escape")).isSameAs(function);
        assertThat(library.getFunction("missing")).isNull();
        assertThat(library.getTagLibraryInfos()).containsExactly(nested);
        assertThat(tagInfo.getTagLibrary()).isSameAs(library);
    }

    @Test
    void tagSupportManagesLifecycleValuesAndAncestors() throws JspException {
        TagSupport root = new TagSupport();
        TagSupport parent = new TagSupport();
        TagSupport child = new TagSupport();
        parent.setParent(root);
        child.setParent(parent);
        child.setId("childId");
        child.setValue("answer", 42);
        child.setValue("name", "jsp");

        assertThat(child.doStartTag()).isEqualTo(Tag.SKIP_BODY);
        assertThat(child.doAfterBody()).isEqualTo(IterationTag.SKIP_BODY);
        assertThat(child.doEndTag()).isEqualTo(Tag.EVAL_PAGE);
        assertThat(child.getParent()).isSameAs(parent);
        assertThat(child.getId()).isEqualTo("childId");
        assertThat(child.getValue("answer")).isEqualTo(42);
        assertThat(Collections.list(child.getValues())).containsExactlyInAnyOrder("answer", "name");
        assertThat(TagSupport.findAncestorWithClass(child, TagSupport.class)).isSameAs(parent);
        assertThat(TagSupport.findAncestorWithClass(child, Tag.class)).isSameAs(parent);
        assertThat(TagSupport.findAncestorWithClass(child, Runnable.class)).isNull();

        child.removeValue("answer");
        assertThat(child.getValue("answer")).isNull();
        child.release();
        assertThat(child.getParent()).isNull();
        assertThat(child.getId()).isNull();
        assertThat(child.getValues()).isNull();
    }

    @Test
    void bodyTagSupportTracksBodyContentAndPreviousWriter() throws JspException, IOException {
        RecordingJspWriter enclosingWriter = new RecordingJspWriter(64, true);
        RecordingBodyContent bodyContent = new RecordingBodyContent(enclosingWriter);
        BodyTagSupport tag = new BodyTagSupport();

        assertThat(tag.doStartTag()).isEqualTo(BodyTag.EVAL_BODY_BUFFERED);
        tag.setBodyContent(bodyContent);
        tag.doInitBody();

        assertThat(tag.getBodyContent()).isSameAs(bodyContent);
        assertThat(tag.getPreviousOut()).isSameAs(enclosingWriter);
        assertThat(tag.doAfterBody()).isEqualTo(IterationTag.SKIP_BODY);
        assertThat(tag.doEndTag()).isEqualTo(Tag.EVAL_PAGE);

        tag.release();
        assertThat(tag.getBodyContent()).isNull();
    }

    @Test
    void simpleTagSupportAndTagAdapterResolveParents() {
        SimpleTagSupport grandParent = new SimpleTagSupport();
        SimpleTagSupport parent = new SimpleTagSupport();
        SimpleTagSupport child = new SimpleTagSupport();
        parent.setParent(grandParent);
        child.setParent(parent);

        assertThat(child.getParent()).isSameAs(parent);
        assertThat(SimpleTagSupport.findAncestorWithClass(child, SimpleTagSupport.class)).isSameAs(parent);
        assertThat(SimpleTagSupport.findAncestorWithClass(child, JspTag.class)).isSameAs(parent);
        assertThat(SimpleTagSupport.findAncestorWithClass(child, Runnable.class)).isNull();

        TagAdapter adapter = new TagAdapter(child);
        assertThat(adapter.getAdaptee()).isSameAs(child);
        assertThat(adapter.getParent())
                .isInstanceOf(TagAdapter.class)
                .extracting(parentAdapter -> ((TagAdapter) parentAdapter).getAdaptee())
                .isSameAs(parent);
        assertThat(adapter.getParent()).isSameAs(adapter.getParent());

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new TagAdapter(null));
        assertThatThrownBy(() -> adapter.setParent(new TagSupport()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("setParent");
        assertThatThrownBy(adapter::doStartTag)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("doStartTag");
        assertThatThrownBy(adapter::doEndTag)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("doEndTag");
        assertThatThrownBy(adapter::release)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("release");
    }

    @Test
    void jspWriterAndBodyContentImplementBufferAndWriterContracts() throws IOException {
        RecordingJspWriter enclosingWriter = new RecordingJspWriter(128, true);
        RecordingBodyContent bodyContent = new RecordingBodyContent(enclosingWriter);

        bodyContent.print("Hello");
        bodyContent.print(' ');
        bodyContent.println("JSP");
        bodyContent.write("body".toCharArray(), 0, 4);

        assertThat(bodyContent.getBufferSize()).isEqualTo(JspWriter.UNBOUNDED_BUFFER);
        assertThat(bodyContent.isAutoFlush()).isFalse();
        assertThat(bodyContent.getRemaining()).isEqualTo(Integer.MAX_VALUE);
        assertThat(bodyContent.getEnclosingWriter()).isSameAs(enclosingWriter);
        assertThat(bodyContent.getString()).isEqualTo("Hello JSP" + System.lineSeparator() + "body");
        assertThat(readAll(bodyContent.getReader())).isEqualTo(bodyContent.getString());

        StringWriter copy = new StringWriter();
        bodyContent.writeOut(copy);
        assertThat(copy).hasToString(bodyContent.getString());

        bodyContent.clearBody();
        assertThat(bodyContent.getString()).isEmpty();
        assertThatThrownBy(bodyContent::flush)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Illegal to flush");

        enclosingWriter.print(true);
        enclosingWriter.print(7);
        enclosingWriter.print(3.5D);
        enclosingWriter.println();
        enclosingWriter.print((Object) "done");
        assertThat(enclosingWriter.getString()).isEqualTo("true73.5" + System.lineSeparator() + "done");
        enclosingWriter.clearBuffer();
        assertThat(enclosingWriter.getString()).isEmpty();
        enclosingWriter.close();
        assertThat(enclosingWriter.isClosed()).isTrue();
    }

    @Test
    void tagLibraryValidatorStoresAndReleasesInitParameters() {
        TagLibraryValidator validator = new TagLibraryValidator() {
        };
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("strict", Boolean.TRUE);
        parameters.put("prefix", "ui");

        validator.setInitParameters(parameters);

        assertThat(validator.getInitParameters()).isSameAs(parameters);
        assertThat(validator.validate("ui", "urn:ui", null)).isNull();
        validator.release();
        assertThat(validator.getInitParameters()).isSameAs(parameters);
    }

    private static final class RecordingJspFactory extends JspFactory {
        private final RecordingJspApplicationContext applicationContext = new RecordingJspApplicationContext();
        private final JspEngineInfo engineInfo = new JspEngineInfo() {
            @Override
            public String getSpecificationVersion() {
                return "test-specification";
            }
        };
        private final List<PageContext> releasedPageContexts = new ArrayList<>();
        private String lastErrorPageUrl;
        private boolean lastSessionRequired;
        private int lastBufferSize;
        private boolean lastAutoFlush;

        private RecordingJspApplicationContext getApplicationContext() {
            return applicationContext;
        }

        private String getLastErrorPageUrl() {
            return lastErrorPageUrl;
        }

        private boolean isLastSessionRequired() {
            return lastSessionRequired;
        }

        private int getLastBufferSize() {
            return lastBufferSize;
        }

        private boolean isLastAutoFlush() {
            return lastAutoFlush;
        }

        private List<PageContext> getReleasedPageContexts() {
            return releasedPageContexts;
        }

        @Override
        public PageContext getPageContext(Servlet servlet, ServletRequest request, ServletResponse response,
                String errorPageUrl, boolean needsSession, int bufferSize, boolean autoFlush) {
            lastErrorPageUrl = errorPageUrl;
            lastSessionRequired = needsSession;
            lastBufferSize = bufferSize;
            lastAutoFlush = autoFlush;
            return null;
        }

        @Override
        public void releasePageContext(PageContext pageContext) {
            releasedPageContexts.add(pageContext);
        }

        @Override
        public JspEngineInfo getEngineInfo() {
            return engineInfo;
        }

        @Override
        public JspApplicationContext getJspApplicationContext(ServletContext context) {
            return applicationContext;
        }
    }

    private static final class RecordingJspApplicationContext implements JspApplicationContext {
        private final List<ELResolver> resolvers = new ArrayList<>();
        private final List<ELContextListener> listeners = new ArrayList<>();

        private List<ELResolver> getResolvers() {
            return resolvers;
        }

        private List<ELContextListener> getListeners() {
            return listeners;
        }

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

    @SuppressWarnings({"deprecation", "removal"})
    private static final class ErrorPageContext extends PageContext {
        private final ServletRequest request;

        private ErrorPageContext(ServletRequest request) {
            this.request = request;
        }

        @Override
        public void initialize(Servlet servlet, ServletRequest request, ServletResponse response, String errorPageUrl,
                boolean needsSession, int bufferSize, boolean autoFlush) {
        }

        @Override
        public void release() {
        }

        @Override
        public HttpSession getSession() {
            return null;
        }

        @Override
        public Object getPage() {
            return null;
        }

        @Override
        public ServletRequest getRequest() {
            return request;
        }

        @Override
        public ServletResponse getResponse() {
            return null;
        }

        @Override
        public Exception getException() {
            return null;
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
        }

        @Override
        public void handlePageException(Throwable throwable) {
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setAttribute(String name, Object value, int scope) {
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public Object getAttribute(String name, int scope) {
            return null;
        }

        @Override
        public Object findAttribute(String name) {
            return null;
        }

        @Override
        public void removeAttribute(String name) {
        }

        @Override
        public void removeAttribute(String name, int scope) {
        }

        @Override
        public int getAttributesScope(String name) {
            return 0;
        }

        @Override
        public Enumeration<String> getAttributeNamesInScope(int scope) {
            return Collections.emptyEnumeration();
        }

        @Override
        public JspWriter getOut() {
            return null;
        }

        @Override
        public ExpressionEvaluator getExpressionEvaluator() {
            return null;
        }

        @Override
        public VariableResolver getVariableResolver() {
            return null;
        }

        @Override
        public ELContext getELContext() {
            return null;
        }
    }

    private static final class ErrorAttributesRequest implements ServletRequest {
        private final Map<String, Object> attributes = new HashMap<>();

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
        public long getContentLengthLong() {
            return 0L;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public ServletInputStream getInputStream() {
            return null;
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
            return Collections.emptyMap();
        }

        @Override
        public String getProtocol() {
            return null;
        }

        @Override
        public String getScheme() {
            return null;
        }

        @Override
        public String getServerName() {
            return null;
        }

        @Override
        public int getServerPort() {
            return 0;
        }

        @Override
        public BufferedReader getReader() {
            return null;
        }

        @Override
        public String getRemoteAddr() {
            return null;
        }

        @Override
        public String getRemoteHost() {
            return null;
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
            return Locale.getDefault();
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return Collections.enumeration(Collections.singleton(Locale.getDefault()));
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
        public int getRemotePort() {
            return 0;
        }

        @Override
        public String getLocalName() {
            return null;
        }

        @Override
        public String getLocalAddr() {
            return null;
        }

        @Override
        public int getLocalPort() {
            return 0;
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public AsyncContext startAsync() {
            throw new IllegalStateException("Async processing is not supported by this request");
        }

        @Override
        public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
            throw new IllegalStateException("Async processing is not supported by this request");
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

        @Override
        public String getRequestId() {
            return null;
        }

        @Override
        public String getProtocolRequestId() {
            return null;
        }

        @Override
        public ServletConnection getServletConnection() {
            return null;
        }
    }

    private static final class RecordingElResolver extends ELResolver {
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
            return false;
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return Object.class;
        }
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

    private static final class RecordingTagExtraInfo extends TagExtraInfo {
        private final boolean valid;
        private final VariableInfo[] variables;

        private RecordingTagExtraInfo(boolean valid, VariableInfo[] variables) {
            this.valid = valid;
            this.variables = variables;
        }

        @Override
        public VariableInfo[] getVariableInfo(TagData data) {
            return variables;
        }

        @Override
        public boolean isValid(TagData data) {
            return valid;
        }
    }

    private static final class TestTagLibraryInfo extends TagLibraryInfo {
        private TagLibraryInfo[] libraries = new TagLibraryInfo[0];

        private TestTagLibraryInfo(String prefix, String uri) {
            super(prefix, uri);
            shortname = prefix + "-tags";
            urn = uri + ":reliable";
            jspversion = "3.1";
            info = "Test tag library";
        }

        private void configure(TagInfo[] tagInfos, TagFileInfo[] tagFileInfos, FunctionInfo[] functionInfos,
                TagLibraryInfo[] nestedLibraries) {
            tags = tagInfos;
            tagFiles = tagFileInfos;
            functions = functionInfos;
            libraries = nestedLibraries;
        }

        @Override
        public TagLibraryInfo[] getTagLibraryInfos() {
            return libraries;
        }
    }

    private static class RecordingJspWriter extends JspWriter {
        private final StringBuilder output = new StringBuilder();
        private boolean closed;

        RecordingJspWriter(int bufferSize, boolean autoFlush) {
            super(bufferSize, autoFlush);
        }

        String getString() {
            return output.toString();
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void newLine() {
            output.append(System.lineSeparator());
        }

        @Override
        public void print(boolean value) {
            output.append(value);
        }

        @Override
        public void print(char value) {
            output.append(value);
        }

        @Override
        public void print(int value) {
            output.append(value);
        }

        @Override
        public void print(long value) {
            output.append(value);
        }

        @Override
        public void print(float value) {
            output.append(value);
        }

        @Override
        public void print(double value) {
            output.append(value);
        }

        @Override
        public void print(char[] value) {
            output.append(value);
        }

        @Override
        public void print(String value) {
            output.append(value == null ? "null" : value);
        }

        @Override
        public void print(Object value) {
            output.append(value);
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
            output.setLength(0);
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
            closed = true;
        }

        @Override
        public int getRemaining() {
            return Math.max(0, getBufferSize() - output.length());
        }

        @Override
        public void write(char[] buffer, int offset, int length) {
            output.append(buffer, offset, length);
        }
    }

    private static final class RecordingBodyContent extends BodyContent {
        private final RecordingJspWriter delegate = new RecordingJspWriter(JspWriter.UNBOUNDED_BUFFER, false);

        private RecordingBodyContent(JspWriter enclosingWriter) {
            super(enclosingWriter);
        }

        @Override
        public Reader getReader() {
            return new StringReader(getString());
        }

        @Override
        public String getString() {
            return delegate.getString();
        }

        @Override
        public void writeOut(Writer writer) throws IOException {
            writer.write(getString());
        }

        @Override
        public void newLine() {
            delegate.newLine();
        }

        @Override
        public void print(boolean value) {
            delegate.print(value);
        }

        @Override
        public void print(char value) {
            delegate.print(value);
        }

        @Override
        public void print(int value) {
            delegate.print(value);
        }

        @Override
        public void print(long value) {
            delegate.print(value);
        }

        @Override
        public void print(float value) {
            delegate.print(value);
        }

        @Override
        public void print(double value) {
            delegate.print(value);
        }

        @Override
        public void print(char[] value) {
            delegate.print(value);
        }

        @Override
        public void print(String value) {
            delegate.print(value);
        }

        @Override
        public void print(Object value) {
            delegate.print(value);
        }

        @Override
        public void println() {
            delegate.println();
        }

        @Override
        public void println(boolean value) {
            delegate.println(value);
        }

        @Override
        public void println(char value) {
            delegate.println(value);
        }

        @Override
        public void println(int value) {
            delegate.println(value);
        }

        @Override
        public void println(long value) {
            delegate.println(value);
        }

        @Override
        public void println(float value) {
            delegate.println(value);
        }

        @Override
        public void println(double value) {
            delegate.println(value);
        }

        @Override
        public void println(char[] value) {
            delegate.println(value);
        }

        @Override
        public void println(String value) {
            delegate.println(value);
        }

        @Override
        public void println(Object value) {
            delegate.println(value);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public void clearBuffer() {
            delegate.clearBuffer();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public int getRemaining() {
            return Integer.MAX_VALUE;
        }

        @Override
        public void write(char[] buffer, int offset, int length) {
            delegate.write(buffer, offset, length);
        }
    }
}
