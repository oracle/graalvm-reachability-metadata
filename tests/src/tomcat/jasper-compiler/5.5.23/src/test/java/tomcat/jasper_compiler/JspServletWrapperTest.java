/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.jsp.tagext.TagInfo;

import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldLocationsCache;
import org.apache.jasper.runtime.JspSourceDependent;
import org.apache.jasper.servlet.JspServletWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JspServletWrapperTest {
    private final List<JspServletWrapper> wrappers = new ArrayList<>();
    private final List<JspRuntimeContext> runtimeContexts = new ArrayList<>();
    private final List<URLClassLoader> runtimeLoaders = new ArrayList<>();

    @TempDir
    Path scratchDirectory;

    @AfterEach
    void closeRuntimeResources() throws Exception {
        try {
            for (JspServletWrapper wrapper : wrappers) {
                wrapper.destroy();
            }
            for (JspRuntimeContext runtimeContext : runtimeContexts) {
                runtimeContext.destroy();
            }
        } finally {
            for (URLClassLoader runtimeLoader : runtimeLoaders) {
                runtimeLoader.close();
            }
        }
    }

    @Test
    void constructorResolvesWrapperClassLiteralThroughGeneratedHelper() throws Exception {
        clearCachedWrapperClassLiteral();

        final JspServletWrapper wrapper = newWrapper(DependentServlet.class);

        assertThat(wrapper.isTagFile()).isTrue();
    }

    @Test
    void getServletInstantiatesLoadedServletClass() throws Exception {
        final JspServletWrapper wrapper = newWrapper(DependentServlet.class);

        final Servlet servlet = wrapper.getServlet();

        assertThat(servlet).isInstanceOf(DependentServlet.class);
        assertThat(((DependentServlet) servlet).wasInitialized()).isTrue();
    }

    @Test
    void getDependantsInstantiatesLoadedTagHandlerClass() throws Exception {
        final JspServletWrapper wrapper = newWrapper(DependentServlet.class);

        final List dependants = wrapper.getDependants();

        assertThat(dependants).containsExactly("/WEB-INF/tags/dependency.tag");
    }

    private JspServletWrapper newWrapper(Class<?> tagHandlerClass) throws Exception {
        final TestOptions options = new TestOptions(scratchDirectory.toFile());
        final MinimalServletContext servletContext = new MinimalServletContext(scratchDirectory);
        final TagInfo tagInfo = new TagInfo(
                "dependency",
                tagHandlerClass.getName(),
                "empty",
                "test tag",
                null,
                null,
                null);
        final JspRuntimeContext runtimeContext = newRuntimeContext(servletContext, options);
        final JspServletWrapper wrapper = new JspServletWrapper(
                servletContext,
                options,
                "/WEB-INF/tags/dependency.tag",
                tagInfo,
                runtimeContext,
                null);
        final JspCompilationContext context = wrapper.getJspEngineContext();
        context.getOutputDir();
        wrappers.add(wrapper);
        return wrapper;
    }

    private JspRuntimeContext newRuntimeContext(ServletContext servletContext, Options options) throws Exception {
        final ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader parentClassLoader = originalContextClassLoader == null
                ? JspServletWrapperTest.class.getClassLoader()
                : originalContextClassLoader;
        final URLClassLoader runtimeParentLoader = new URLClassLoader(new URL[0], parentClassLoader);
        runtimeLoaders.add(runtimeParentLoader);
        Thread.currentThread().setContextClassLoader(runtimeParentLoader);
        try {
            final JspRuntimeContext runtimeContext = new JspRuntimeContext(servletContext, options);
            runtimeContexts.add(runtimeContext);
            return runtimeContext;
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static void clearCachedWrapperClassLiteral() throws Exception {
        final String[] fieldNameParts = new String[] {
                "class$org$apache$jasper$servlet$",
                "JspServletWrapper"
        };
        final VarHandle cachedClassLiteral = MethodHandles.privateLookupIn(
                JspServletWrapper.class,
                MethodHandles.lookup()).findStaticVarHandle(
                        JspServletWrapper.class,
                        fieldNameParts[0] + fieldNameParts[1],
                        Class.class);
        cachedClassLiteral.set(null);
    }

    public static final class DependentServlet implements Servlet, JspSourceDependent {
        private boolean initialized;
        private ServletConfig config;

        public DependentServlet() {
        }

        @Override
        public void init(ServletConfig config) {
            initialized = true;
            this.config = config;
        }

        @Override
        public ServletConfig getServletConfig() {
            return config;
        }

        @Override
        public void service(ServletRequest request, ServletResponse response) throws IOException {
            response.getWriter().write("dependent servlet");
        }

        @Override
        public String getServletInfo() {
            return "dependent servlet";
        }

        @Override
        public void destroy() {
            config = null;
        }

        @Override
        public Object getDependants() {
            return Collections.singletonList("/WEB-INF/tags/dependency.tag");
        }

        private boolean wasInitialized() {
            return initialized;
        }
    }

    private static final class TestOptions implements Options {
        private final File scratchDirectory;
        private final Map cache = new HashMap();

        private TestOptions(File scratchDirectory) {
            this.scratchDirectory = scratchDirectory;
        }

        @Override
        public boolean getErrorOnUseBeanInvalidClassAttribute() {
            return true;
        }

        @Override
        public boolean getKeepGenerated() {
            return false;
        }

        @Override
        public boolean isPoolingEnabled() {
            return false;
        }

        @Override
        public boolean getMappedFile() {
            return false;
        }

        @Override
        public boolean getSendErrorToClient() {
            return false;
        }

        @Override
        public boolean getClassDebugInfo() {
            return false;
        }

        @Override
        public int getCheckInterval() {
            return 0;
        }

        @Override
        public boolean getDevelopment() {
            return true;
        }

        @Override
        public boolean isSmapSuppressed() {
            return true;
        }

        @Override
        public boolean isSmapDumped() {
            return false;
        }

        @Override
        public boolean getTrimSpaces() {
            return false;
        }

        @Override
        public String getIeClassId() {
            return "";
        }

        @Override
        public File getScratchDir() {
            return scratchDirectory;
        }

        @Override
        public String getClassPath() {
            return System.getProperty("java.class.path");
        }

        @Override
        public String getCompiler() {
            return null;
        }

        @Override
        public String getCompilerTargetVM() {
            return "1.8";
        }

        @Override
        public String getCompilerSourceVM() {
            return "1.8";
        }

        @Override
        public TldLocationsCache getTldLocationsCache() {
            return null;
        }

        @Override
        public String getJavaEncoding() {
            return StandardCharsets.UTF_8.name();
        }

        @Override
        public boolean getFork() {
            return false;
        }

        @Override
        public JspConfig getJspConfig() {
            return null;
        }

        @Override
        public boolean isXpoweredBy() {
            return false;
        }

        @Override
        public TagPluginManager getTagPluginManager() {
            return null;
        }

        @Override
        public boolean genStringAsCharArray() {
            return false;
        }

        @Override
        public int getModificationTestInterval() {
            return 0;
        }

        @Override
        public boolean isCaching() {
            return false;
        }

        @Override
        public Map getCache() {
            return cache;
        }
    }

    private static final class MinimalServletContext implements ServletContext {
        private final Path webRoot;

        private MinimalServletContext(Path webRoot) {
            this.webRoot = webRoot;
        }

        @Override
        public ServletContext getContext(String uripath) {
            return null;
        }

        @Override
        public int getMajorVersion() {
            return 2;
        }

        @Override
        public int getMinorVersion() {
            return 4;
        }

        @Override
        public String getMimeType(String file) {
            return null;
        }

        @Override
        public Set getResourcePaths(String path) {
            return Collections.emptySet();
        }

        @Override
        public URL getResource(String path) throws MalformedURLException {
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
        public Servlet getServlet(String name) throws ServletException {
            return null;
        }

        @Override
        public Enumeration getServlets() {
            return Collections.emptyEnumeration();
        }

        @Override
        public Enumeration getServletNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public void log(String msg) {
            // The minimal context does not retain log messages.
        }

        @Override
        public void log(Exception exception, String msg) {
            // The minimal context does not retain log messages.
        }

        @Override
        public void log(String message, Throwable throwable) {
            // The minimal context does not retain log messages.
        }

        @Override
        public String getRealPath(String path) {
            return webRoot.resolve(path.replaceFirst("^/", "")).toString();
        }

        @Override
        public String getServerInfo() {
            return "minimal-servlet-context";
        }

        @Override
        public String getInitParameter(String name) {
            return null;
        }

        @Override
        public Enumeration getInitParameterNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public Enumeration getAttributeNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public void setAttribute(String name, Object object) {
            // The minimal context is immutable.
        }

        @Override
        public void removeAttribute(String name) {
            // The minimal context is immutable.
        }

        @Override
        public String getServletContextName() {
            return "minimal";
        }
    }
}
