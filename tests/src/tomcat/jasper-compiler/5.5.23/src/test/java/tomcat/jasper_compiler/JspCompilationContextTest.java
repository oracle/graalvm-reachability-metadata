/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.Compiler;
import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldLocationsCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JspCompilationContextTest {
    @TempDir
    Path scratchDirectory;

    @Test
    void constructorResolvesClassLiteralThroughGeneratedHelper() throws Exception {
        clearCachedClassLiteral();

        final TestOptions options = new TestOptions(scratchDirectory.toFile());
        final JspCompilationContext context = new JspCompilationContext(
                "/index.jsp",
                false,
                options,
                new MinimalServletContext(scratchDirectory),
                null,
                null);

        assertThat(context.getJspFile()).isEqualTo("/index.jsp");
    }

    @Test
    void createCompilerInstantiatesDefaultCompilerImplementation() throws Exception {
        final TestOptions options = new TestOptions(scratchDirectory.toFile());
        final JspCompilationContext context = new JspCompilationContext(
                "/index.jsp",
                false,
                options,
                new MinimalServletContext(scratchDirectory),
                null,
                null);

        final Compiler compiler = context.createCompiler();

        assertThat(compiler).isNotNull();
        assertThat(context.getCompiler()).isSameAs(compiler);
        assertThat(context.createCompiler()).isSameAs(compiler);
    }

    @Test
    void loadResolvesConfiguredServletClassThroughJspLoader() throws Exception {
        final TestOptions options = new TestOptions(scratchDirectory.toFile());
        final MinimalServletContext servletContext = new MinimalServletContext(scratchDirectory);
        final ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader parentClassLoader = originalContextClassLoader == null
                ? JspCompilationContextTest.class.getClassLoader()
                : originalContextClassLoader;
        final URLClassLoader runtimeParentLoader = new URLClassLoader(new URL[0], parentClassLoader);
        JspRuntimeContext runtimeContext = null;

        Thread.currentThread().setContextClassLoader(runtimeParentLoader);
        try {
            runtimeContext = new JspRuntimeContext(servletContext, options);
            final JspCompilationContext context = new JspCompilationContext(
                    "/index.jsp",
                    false,
                    options,
                    servletContext,
                    null,
                    runtimeContext);
            context.setServletPackageName(JspCompilationContextTest.class.getPackageName());
            context.setServletClassName(JspCompilationContextTest.class.getSimpleName());
            context.getOutputDir();

            final Class<?> loadedClass = context.load();

            assertThat(loadedClass).isSameAs(JspCompilationContextTest.class);
        } finally {
            if (runtimeContext != null) {
                runtimeContext.destroy();
            }
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            runtimeParentLoader.close();
        }
    }

    private static void clearCachedClassLiteral() throws Exception {
        final String[] fieldNameParts = new String[] {
                "class$org$apache$jasper$",
                "JspCompilationContext"
        };
        final VarHandle cachedClassLiteral = MethodHandles.privateLookupIn(
                JspCompilationContext.class,
                MethodHandles.lookup()).findStaticVarHandle(
                        JspCompilationContext.class,
                        fieldNameParts[0] + fieldNameParts[1],
                        Class.class);
        cachedClassLiteral.set(null);
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
