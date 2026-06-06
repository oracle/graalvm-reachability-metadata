/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet_jsp_jstl.jakarta_servlet_jsp_jstl_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.beans.FeatureDescriptor;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Serial;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.SortedMap;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ELResolver;
import jakarta.el.FunctionMapper;
import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.ErrorData;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.el.ExpressionEvaluator;
import jakarta.servlet.jsp.el.VariableResolver;
import jakarta.servlet.jsp.jstl.core.ConditionalTagSupport;
import jakarta.servlet.jsp.jstl.core.Config;
import jakarta.servlet.jsp.jstl.core.IndexedValueExpression;
import jakarta.servlet.jsp.jstl.core.IteratedExpression;
import jakarta.servlet.jsp.jstl.core.IteratedValueExpression;
import jakarta.servlet.jsp.jstl.core.LoopTagStatus;
import jakarta.servlet.jsp.jstl.core.LoopTagSupport;
import jakarta.servlet.jsp.jstl.fmt.LocaleSupport;
import jakarta.servlet.jsp.jstl.fmt.LocalizationContext;
import jakarta.servlet.jsp.jstl.sql.Result;
import jakarta.servlet.jsp.jstl.sql.ResultSupport;
import jakarta.servlet.jsp.jstl.tlv.PermittedTaglibsTLV;
import jakarta.servlet.jsp.jstl.tlv.ScriptFreeTLV;
import jakarta.servlet.jsp.tagext.IterationTag;
import jakarta.servlet.jsp.tagext.PageData;
import jakarta.servlet.jsp.tagext.Tag;
import jakarta.servlet.jsp.tagext.ValidationMessage;
import org.junit.jupiter.api.Test;

public class Jakarta_servlet_jsp_jstl_apiTest {
    private static final String NAMED_MESSAGES_BUNDLE = "jakarta_servlet_jsp_jstl.jakarta_servlet_jsp_jstl_api"
            + ".Jakarta_servlet_jsp_jstl_apiTest$NamedLocaleMessages";
    private static final String ROOT_MESSAGES_BUNDLE = "jakarta_servlet_jsp_jstl.jakarta_servlet_jsp_jstl_api"
            + ".Jakarta_servlet_jsp_jstl_apiTest$RootMessages";

    @Test
    void configStoresScopedValuesAndFindsThemInStandardOrder() {
        TestPageContext pageContext = new TestPageContext();

        Config.set(pageContext, Config.FMT_TIME_ZONE, "UTC", PageContext.APPLICATION_SCOPE);
        Config.set(pageContext, Config.FMT_TIME_ZONE, "Europe/Paris", PageContext.REQUEST_SCOPE);
        Config.set(pageContext, Config.SQL_MAX_ROWS, 25, PageContext.PAGE_SCOPE);
        Config.set(pageContext.getServletContext(), Config.SQL_DATA_SOURCE, "jdbc/test");

        assertThat(Config.get(pageContext, Config.FMT_TIME_ZONE, PageContext.REQUEST_SCOPE)).isEqualTo("Europe/Paris");
        assertThat(Config.find(pageContext, Config.FMT_TIME_ZONE)).isEqualTo("Europe/Paris");
        assertThat(Config.find(pageContext, Config.SQL_MAX_ROWS)).isEqualTo(25);
        assertThat(Config.get(pageContext.getServletContext(), Config.SQL_DATA_SOURCE)).isEqualTo("jdbc/test");

        Config.remove(pageContext, Config.FMT_TIME_ZONE, PageContext.REQUEST_SCOPE);
        assertThat(Config.find(pageContext, Config.FMT_TIME_ZONE)).isEqualTo("UTC");

        Config.remove(pageContext.getServletContext(), Config.SQL_DATA_SOURCE);
        assertThat(Config.get(pageContext.getServletContext(), Config.SQL_DATA_SOURCE)).isNull();
        assertThatThrownBy(() -> Config.get(pageContext, Config.FMT_TIME_ZONE, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown scope");
    }

    @Test
    void configFindFallsBackToServletContextInitParameters() {
        TestPageContext pageContext = new TestPageContext();

        assertThat(pageContext.getServletContext().setInitParameter(Config.FMT_LOCALE, "en-US")).isTrue();

        assertThat(Config.get(pageContext.getServletContext(), Config.FMT_LOCALE)).isNull();
        assertThat(Config.find(pageContext, Config.FMT_LOCALE)).isEqualTo("en-US");
    }

    @Test
    void localizationContextAndLocaleSupportResolveAndFormatMessages() {
        TestPageContext pageContext = new TestPageContext();
        ResourceBundle bundle = new MessagesBundle();
        LocalizationContext context = new LocalizationContext(bundle, Locale.US);

        Config.set(pageContext, Config.FMT_LOCALIZATION_CONTEXT, context, PageContext.PAGE_SCOPE);

        assertThat(context.getResourceBundle()).isSameAs(bundle);
        assertThat(context.getLocale()).isEqualTo(Locale.US);
        assertThat(LocaleSupport.getLocalizedMessage(pageContext, "plain")).isEqualTo("Plain text");
        assertThat(LocaleSupport.getLocalizedMessage(pageContext, "welcome", new Object[] {"Ada"}))
                .isEqualTo("Welcome Ada");
        assertThat(LocaleSupport.getLocalizedMessage(pageContext, "missing")).isEqualTo("???missing???");
        assertThat(new LocalizationContext().getResourceBundle()).isNull();
        assertThat(new LocalizationContext(bundle).getLocale()).isNull();
    }

    @Test
    void localeSupportLoadsBundleByBasenameAndAppliesConfiguredLocale() {
        TestPageContext pageContext = new TestPageContext();

        Config.set(pageContext, Config.FMT_LOCALE, "en-US", PageContext.PAGE_SCOPE);

        assertThat(LocaleSupport.getLocalizedMessage(pageContext, "salutation", new Object[] {"Ada"},
                NAMED_MESSAGES_BUNDLE)).isEqualTo("Howdy Ada");
        assertThat(pageContext.getResponse().getLocale()).isEqualTo(Locale.US);
    }

    @Test
    void localeSupportLoadsRootBundleWhenConfiguredLocaleDoesNotMatchAvailableBundles() {
        TestPageContext pageContext = new TestPageContext();

        Config.set(pageContext, Config.FMT_LOCALE, "zz-ZZ", PageContext.PAGE_SCOPE);

        assertThat(LocaleSupport.getLocalizedMessage(pageContext, "rootOnly", ROOT_MESSAGES_BUNDLE))
                .isEqualTo("Resolved from the root bundle");
    }

    @Test
    void conditionalTagExposesBooleanResultInConfiguredScope() throws JspException {
        TestPageContext pageContext = new TestPageContext();
        TestConditionalTag trueTag = new TestConditionalTag(true);
        trueTag.setPageContext(pageContext);
        trueTag.setVar("conditionMet");
        trueTag.setScope("request");

        assertThat(trueTag.doStartTag()).isEqualTo(Tag.EVAL_BODY_INCLUDE);
        assertThat(pageContext.getAttribute("conditionMet", PageContext.REQUEST_SCOPE)).isEqualTo(Boolean.TRUE);

        TestConditionalTag falseTag = new TestConditionalTag(false);
        falseTag.setPageContext(pageContext);
        falseTag.setVar("conditionMet");
        falseTag.setScope("application");

        assertThat(falseTag.doStartTag()).isEqualTo(Tag.SKIP_BODY);
        assertThat(pageContext.getAttribute("conditionMet", PageContext.APPLICATION_SCOPE)).isEqualTo(Boolean.FALSE);

        falseTag.release();
        assertThat(falseTag.doStartTag()).isEqualTo(Tag.SKIP_BODY);
    }

    @Test
    void loopTagSupportIteratesExposesCurrentItemAndStatus() throws JspException {
        TestPageContext pageContext = new TestPageContext();
        TestLoopTag loopTag = new TestLoopTag(List.of("zero", "one", "two", "three"));
        loopTag.setPageContext(pageContext);
        loopTag.setVar("item");
        loopTag.setVarStatus("status");
        loopTag.setBounds(1, 3, 2);

        assertThat(loopTag.doStartTag()).isEqualTo(Tag.EVAL_BODY_INCLUDE);
        assertThat(loopTag.getCurrent()).isEqualTo("one");
        assertThat(pageContext.getAttribute("item")).isEqualTo("one");

        LoopTagStatus firstStatus = (LoopTagStatus) pageContext.getAttribute("status");
        assertThat(firstStatus.getCurrent()).isEqualTo("one");
        assertThat(firstStatus.getIndex()).isEqualTo(1);
        assertThat(firstStatus.getCount()).isEqualTo(1);
        assertThat(firstStatus.isFirst()).isTrue();
        assertThat(firstStatus.isLast()).isFalse();
        assertThat(firstStatus.getBegin()).isEqualTo(1);
        assertThat(firstStatus.getEnd()).isEqualTo(3);
        assertThat(firstStatus.getStep()).isEqualTo(2);

        assertThat(loopTag.doAfterBody()).isEqualTo(IterationTag.EVAL_BODY_AGAIN);
        assertThat(pageContext.getAttribute("item")).isEqualTo("three");
        LoopTagStatus secondStatus = loopTag.getLoopStatus();
        assertThat(secondStatus.getIndex()).isEqualTo(3);
        assertThat(secondStatus.getCount()).isEqualTo(2);
        assertThat(secondStatus.isFirst()).isFalse();
        assertThat(secondStatus.isLast()).isTrue();

        assertThat(loopTag.doAfterBody()).isEqualTo(Tag.SKIP_BODY);
        loopTag.doFinally();
        assertThat(pageContext.getAttribute("item")).isNull();
        assertThat(pageContext.getAttribute("status")).isNull();
    }

    @Test
    void indexedValueExpressionReadsWritesThroughConfiguredElResolver() {
        TestELContext elContext = new TestELContext();
        List<String> colors = new ArrayList<>(List.of("red", "green"));
        ConstantValueExpression original = new ConstantValueExpression(colors, "${colors}");
        IndexedValueExpression expression = new IndexedValueExpression(original, 1);

        assertThat(expression.getValue(elContext)).isEqualTo("green");
        assertThat(expression.isReadOnly(elContext)).isFalse();
        assertThat(expression.getType(elContext)).isEqualTo(Object.class);

        expression.setValue(elContext, "blue");

        assertThat(colors).containsExactly("red", "blue");
        assertThat(expression.getExpectedType()).isEqualTo(Object.class);
        assertThat(expression.getExpressionString()).isEqualTo("${colors}");
        assertThat(expression.isLiteralText()).isFalse();
        assertThat(expression.equals(original)).isTrue();
        assertThat(expression.hashCode()).isEqualTo(original.hashCode());
    }

    @Test
    void iteratedExpressionsExposeCollectionStringEnumerationAndMapItems() {
        TestELContext elContext = new TestELContext();
        IteratedExpression stringItems = new IteratedExpression(
                new ConstantValueExpression("alpha,beta,gamma", "${csv}"), ",");
        IteratedExpression collectionItems = new IteratedExpression(
                new ConstantValueExpression(List.of("first", "second"), "${list}"), ",");
        IteratedExpression enumerationItems = new IteratedExpression(
                new ConstantValueExpression(Collections.enumeration(List.of("north", "south")), "${enum}"), ",");
        IteratedExpression mapItems = new IteratedExpression(
                new ConstantValueExpression(Map.of("name", "Grace"), "${map}"), ",");
        IteratedValueExpression secondStringItem = new IteratedValueExpression(stringItems, 1);

        assertThat(stringItems.getItem(elContext, 2)).isEqualTo("gamma");
        assertThat(stringItems.getItem(elContext, 0)).isEqualTo("alpha");
        assertThat(secondStringItem.getValue(elContext)).isEqualTo("beta");
        assertThat(secondStringItem.isReadOnly(elContext)).isTrue();
        assertThat(secondStringItem.getType(elContext)).isNull();
        assertThat(secondStringItem.getExpectedType()).isEqualTo(Object.class);
        assertThat(secondStringItem.getExpressionString()).isEqualTo("${csv}");
        assertThat(secondStringItem.isLiteralText()).isFalse();
        assertThat(collectionItems.getItem(elContext, 1)).isEqualTo("second");
        assertThat(enumerationItems.getItem(elContext, 0)).isEqualTo("north");
        Map.Entry<?, ?> entry = (Map.Entry<?, ?>) mapItems.getItem(elContext, 0);
        assertThat(entry.getKey()).isEqualTo("name");
        assertThat(entry.getValue()).isEqualTo("Grace");
        assertThat(stringItems.getValueExpression().getExpressionString()).isEqualTo("${csv}");

        IteratedExpression invalidItems = new IteratedExpression(new ConstantValueExpression(42, "${number}"), ",");
        assertThatThrownBy(() -> invalidItems.getItem(elContext, 0)).isInstanceOf(ELException.class);
    }

    @Test
    void resultSupportCopiesResultSetRowsByNameAndIndex() throws SQLException {
        CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(2);
        metadata.setColumnName(1, "ID");
        metadata.setColumnType(1, Types.INTEGER);
        metadata.setColumnName(2, "NAME");
        metadata.setColumnType(2, Types.VARCHAR);
        rowSet.setMetaData(metadata);
        insertRow(rowSet, 1, "Ada");
        insertRow(rowSet, 2, "Grace");
        rowSet.beforeFirst();

        Result limited = ResultSupport.toResult(rowSet, 1);

        assertThat(limited.getColumnNames()).containsExactly("ID", "NAME");
        assertThat(limited.getRowCount()).isEqualTo(1);
        assertThat(limited.isLimitedByMaxRows()).isTrue();
        SortedMap<?, ?> limitedRow = limited.getRows()[0];
        assertThat(limitedRow.get("id")).isIn(1, 2);
        assertThat(limitedRow.get("name")).isIn("Ada", "Grace");

        rowSet.beforeFirst();
        Result allRows = ResultSupport.toResult(rowSet);
        assertThat(allRows.getRowCount()).isEqualTo(2);
        assertThat(allRows.isLimitedByMaxRows()).isFalse();
        assertThat(allRows.getRows()).anySatisfy(row -> {
            assertThat(row.get("id")).isEqualTo(1);
            assertThat(row.get("name")).isEqualTo("Ada");
        }).anySatisfy(row -> {
            assertThat(row.get("id")).isEqualTo(2);
            assertThat(row.get("name")).isEqualTo("Grace");
        });
        Object[][] rowsByIndex = allRows.getRowsByIndex();
        assertThat(Arrays.stream(rowsByIndex).anyMatch(row -> Arrays.equals(row, new Object[] {1, "Ada"}))).isTrue();
        assertThat(Arrays.stream(rowsByIndex).anyMatch(row -> Arrays.equals(row, new Object[] {2, "Grace"}))).isTrue();
    }

    @Test
    void permittedTaglibsValidatorReturnsValidationMessageForInvalidPageData() {
        PermittedTaglibsTLV validator = new PermittedTaglibsTLV();
        validator.setInitParameters(Map.of("permittedTaglibs", "http://allowed.example/tags"));

        ValidationMessage[] messages = validator.validate(
                "prefix", "http://current.example/tags", new StringPageData("<jsp:root>"));

        assertThat(messages).hasSize(1);
        assertThat(messages[0].getId()).isNull();
        assertThat(messages[0].getMessage()).contains("SAX");

        validator.release();
    }

    @Test
    void scriptFreeValidatorReportsDisallowedScriptingAndAllowsConfiguredContent() {
        String jspDocument = """
                <?xml version="1.0"?>
                <jsp:root xmlns:jsp="http://java.sun.com/JSP/Page" version="3.0">
                  <jsp:declaration>int count = 0;</jsp:declaration>
                  <jsp:scriptlet>count++;</jsp:scriptlet>
                  <jsp:expression>count</jsp:expression>
                  <sample value="%= count %" />
                </jsp:root>
                """;
        ScriptFreeTLV strictValidator = new ScriptFreeTLV();

        ValidationMessage[] messages = strictValidator.validate("prefix", "uri", new StringPageData(jspDocument));

        assertThat(messages).hasSize(1);
        assertThat(messages[0].getId()).isNull();
        assertThat(messages[0].getMessage())
                .contains("1 declaration")
                .contains("1 scriptlet")
                .contains("1 expression")
                .contains("1 request-time attribute value");

        ScriptFreeTLV permissiveValidator = new ScriptFreeTLV();
        permissiveValidator.setInitParameters(Map.of(
                "allowDeclarations", "true",
                "allowScriptlets", "true",
                "allowExpressions", "true",
                "allowRTExpressions", "true"));

        assertThat(permissiveValidator.validate("prefix", "uri", new StringPageData(jspDocument))).isNull();
    }

    private static void insertRow(CachedRowSet rowSet, int id, String name) throws SQLException {
        rowSet.moveToInsertRow();
        rowSet.updateInt(1, id);
        rowSet.updateString(2, name);
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
    }

    private static final class TestConditionalTag extends ConditionalTagSupport {
        @Serial
        private static final long serialVersionUID = 1L;

        private boolean result;

        private TestConditionalTag(boolean result) {
            this.result = result;
        }

        @Override
        protected boolean condition() {
            return result;
        }
    }

    private static final class TestLoopTag extends LoopTagSupport {
        @Serial
        private static final long serialVersionUID = 1L;

        private final List<?> items;
        private Iterator<?> iterator;

        private TestLoopTag(List<?> items) {
            this.items = items;
        }

        private void setBounds(int begin, int end, int step) {
            this.begin = begin;
            this.end = end;
            this.step = step;
            this.beginSpecified = true;
            this.endSpecified = true;
            this.stepSpecified = true;
        }

        @Override
        protected Object next() {
            return iterator.next();
        }

        @Override
        protected boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        protected void prepare() {
            iterator = items.iterator();
        }
    }

    private static final class ConstantValueExpression extends ValueExpression {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Object value;
        private final String expression;

        private ConstantValueExpression(Object value, String expression) {
            this.value = value;
            this.expression = expression;
        }

        @Override
        public Object getValue(ELContext context) {
            return value;
        }

        @Override
        public void setValue(ELContext context, Object value) {
            throw new UnsupportedOperationException("Constant test value cannot be changed");
        }

        @Override
        public boolean isReadOnly(ELContext context) {
            return true;
        }

        @Override
        public Class<?> getType(ELContext context) {
            return value == null ? null : value.getClass();
        }

        @Override
        public Class<?> getExpectedType() {
            return Object.class;
        }

        @Override
        public String getExpressionString() {
            return expression;
        }

        @Override
        public boolean equals(Object object) {
            return this == object;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean isLiteralText() {
            return false;
        }
    }

    private static final class TestELContext extends ELContext {
        private final ELResolver resolver = new TestELResolver();
        private final VariableMapper variableMapper = new TestVariableMapper();

        @Override
        public ELResolver getELResolver() {
            return resolver;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return null;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return variableMapper;
        }
    }

    private static final class TestELResolver extends ELResolver {
        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            if (base instanceof List<?> list && property instanceof Number number) {
                context.setPropertyResolved(true);
                return list.get(number.intValue());
            }
            if (base instanceof Map<?, ?> map) {
                context.setPropertyResolved(true);
                return map.get(property);
            }
            return null;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void setValue(ELContext context, Object base, Object property, Object value) {
            if (base instanceof List list && property instanceof Number number) {
                context.setPropertyResolved(true);
                list.set(number.intValue(), value);
            }
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            if (base instanceof List<?> && property instanceof Number) {
                context.setPropertyResolved(true);
                return false;
            }
            return true;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            if (base instanceof List<?> && property instanceof Number) {
                context.setPropertyResolved(true);
                return Object.class;
            }
            return null;
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

    private static final class TestVariableMapper extends VariableMapper {
        private final Map<String, ValueExpression> variables = new HashMap<>();

        @Override
        public ValueExpression resolveVariable(String variable) {
            return variables.get(variable);
        }

        @Override
        public ValueExpression setVariable(String variable, ValueExpression expression) {
            return variables.put(variable, expression);
        }
    }

    private static final class StringPageData extends PageData {
        private final String xml;

        private StringPageData(String xml) {
            this.xml = xml;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static final class MessagesBundle extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    {"plain", "Plain text"},
                    {"welcome", "Welcome {0}"}
            };
        }
    }

    public static final class NamedLocaleMessages extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    {"salutation", "Hello {0}"}
            };
        }
    }

    public static final class NamedLocaleMessages_en_US extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    {"salutation", "Howdy {0}"}
            };
        }
    }

    public static final class RootMessages extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    {"rootOnly", "Resolved from the root bundle"}
            };
        }
    }

    private static final class TestPageContext extends PageContext {
        private final Map<Integer, Map<String, Object>> scopedAttributes = new HashMap<>();
        private final TestServletContext servletContext = new TestServletContext();
        private final TestHttpSession session = new TestHttpSession(servletContext);
        private final TestServletResponse response = new TestServletResponse();
        private final TestELContext elContext = new TestELContext();

        private TestPageContext() {
            scopedAttributes.put(PAGE_SCOPE, new HashMap<>());
            scopedAttributes.put(REQUEST_SCOPE, new HashMap<>());
            scopedAttributes.put(SESSION_SCOPE, new HashMap<>());
            scopedAttributes.put(APPLICATION_SCOPE, new HashMap<>());
        }

        @Override
        public void setAttribute(String name, Object value) {
            setAttribute(name, value, PAGE_SCOPE);
        }

        @Override
        public void setAttribute(String name, Object value, int scope) {
            scopedAttributes.get(scope).put(name, value);
            if (scope == SESSION_SCOPE) {
                session.setAttribute(name, value);
            } else if (scope == APPLICATION_SCOPE) {
                servletContext.setAttribute(name, value);
            }
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
            for (int scope : List.of(PAGE_SCOPE, REQUEST_SCOPE, SESSION_SCOPE, APPLICATION_SCOPE)) {
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
            session.removeAttribute(name);
            servletContext.removeAttribute(name);
        }

        @Override
        public void removeAttribute(String name, int scope) {
            scopedAttributes.get(scope).remove(name);
            if (scope == SESSION_SCOPE) {
                session.removeAttribute(name);
            } else if (scope == APPLICATION_SCOPE) {
                servletContext.removeAttribute(name);
            }
        }

        @Override
        public int getAttributesScope(String name) {
            for (int scope : List.of(PAGE_SCOPE, REQUEST_SCOPE, SESSION_SCOPE, APPLICATION_SCOPE)) {
                if (scopedAttributes.get(scope).containsKey(name)) {
                    return scope;
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
            return elContext;
        }

        @Override
        public void initialize(Servlet servlet, ServletRequest request, ServletResponse response, String errorPageUrl,
                boolean needsSession, int bufferSize, boolean autoFlush) {
        }

        @Override
        public void release() {
            removeAttribute("*");
        }

        @Override
        public HttpSession getSession() {
            return session;
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
            return response;
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
            return servletContext;
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
        public ErrorData getErrorData() {
            return null;
        }
    }

    private static final class TestHttpSession implements HttpSession {
        private final ServletContext servletContext;
        private final Map<String, Object> attributes = new HashMap<>();

        private TestHttpSession(ServletContext servletContext) {
            this.servletContext = servletContext;
        }

        @Override
        public long getCreationTime() {
            return 0;
        }

        @Override
        public String getId() {
            return "test-session";
        }

        @Override
        public long getLastAccessedTime() {
            return 0;
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
        }

        @Override
        public void setMaxInactiveInterval(int interval) {
        }

        @Override
        public int getMaxInactiveInterval() {
            return 0;
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
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        @Override
        public void invalidate() {
            attributes.clear();
        }

        @Override
        public boolean isNew() {
            return false;
        }
    }

    private static final class TestServletResponse implements ServletResponse {
        private Locale locale = Locale.getDefault();

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return null;
        }

        @Override
        public PrintWriter getWriter() {
            return new PrintWriter(System.out);
        }

        @Override
        public void setCharacterEncoding(String charset) {
        }

        @Override
        public void setContentLength(int length) {
        }

        @Override
        public void setContentLengthLong(long length) {
        }

        @Override
        public void setContentType(String type) {
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
        public void setLocale(Locale locale) {
            this.locale = locale;
        }

        @Override
        public Locale getLocale() {
            return locale;
        }
    }

    private static final class TestServletContext implements ServletContext {
        private final Map<String, Object> attributes = new HashMap<>();
        private final Map<String, String> initParameters = new HashMap<>();

        @Override
        public String getContextPath() {
            return "";
        }

        @Override
        public ServletContext getContext(String uripath) {
            return null;
        }

        @Override
        public int getMajorVersion() {
            return 6;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public int getEffectiveMajorVersion() {
            return 6;
        }

        @Override
        public int getEffectiveMinorVersion() {
            return 0;
        }

        @Override
        public String getMimeType(String file) {
            return null;
        }

        @Override
        public Set<String> getResourcePaths(String path) {
            return Collections.emptySet();
        }

        @Override
        public java.net.URL getResource(String path) {
            return null;
        }

        @Override
        public InputStream getResourceAsStream(String path) {
            return null;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            return null;
        }

        @Override
        public RequestDispatcher getNamedDispatcher(String name) {
            return null;
        }

        @Override
        public void log(String message) {
        }

        @Override
        public void log(String message, Throwable throwable) {
        }

        @Override
        public String getRealPath(String path) {
            return null;
        }

        @Override
        public String getServerInfo() {
            return "test";
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
        public boolean setInitParameter(String name, String value) {
            initParameters.put(name, value);
            return true;
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
        public void setAttribute(String name, Object object) {
            attributes.put(name, object);
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        @Override
        public String getServletContextName() {
            return "test-context";
        }

        @Override
        public ServletRegistration.Dynamic addServlet(String servletName, String className) {
            return null;
        }

        @Override
        public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
            return null;
        }

        @Override
        public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
            return null;
        }

        @Override
        public ServletRegistration.Dynamic addJspFile(String servletName, String jspFile) {
            return null;
        }

        @Override
        public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
            return null;
        }

        @Override
        public ServletRegistration getServletRegistration(String servletName) {
            return null;
        }

        @Override
        public Map<String, ? extends ServletRegistration> getServletRegistrations() {
            return Collections.emptyMap();
        }

        @Override
        public FilterRegistration.Dynamic addFilter(String filterName, String className) {
            return null;
        }

        @Override
        public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
            return null;
        }

        @Override
        public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
            return null;
        }

        @Override
        public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
            return null;
        }

        @Override
        public FilterRegistration getFilterRegistration(String filterName) {
            return null;
        }

        @Override
        public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
            return Collections.emptyMap();
        }

        @Override
        public SessionCookieConfig getSessionCookieConfig() {
            return null;
        }

        @Override
        public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
        }

        @Override
        public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
            return Collections.emptySet();
        }

        @Override
        public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
            return Collections.emptySet();
        }

        @Override
        public void addListener(String className) {
        }

        @Override
        public <T extends EventListener> void addListener(T listener) {
        }

        @Override
        public void addListener(Class<? extends EventListener> listenerClass) {
        }

        @Override
        public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
            return null;
        }

        @Override
        public JspConfigDescriptor getJspConfigDescriptor() {
            return null;
        }

        @Override
        public ClassLoader getClassLoader() {
            return Thread.currentThread().getContextClassLoader();
        }

        @Override
        public void declareRoles(String... roleNames) {
        }

        @Override
        public String getVirtualServerName() {
            return "test";
        }

        @Override
        public int getSessionTimeout() {
            return 0;
        }

        @Override
        public void setSessionTimeout(int sessionTimeout) {
        }

        @Override
        public String getRequestCharacterEncoding() {
            return null;
        }

        @Override
        public void setRequestCharacterEncoding(String encoding) {
        }

        @Override
        public String getResponseCharacterEncoding() {
            return null;
        }

        @Override
        public void setResponseCharacterEncoding(String encoding) {
        }
    }
}
