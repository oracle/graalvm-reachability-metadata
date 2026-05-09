/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.Compiler;
import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldLocationsCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ValidatorAnonymous1MapperELVisitorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void validatesElFunctionByLoadingFunctionClassAndResolvingDeclaredMethod() throws Exception {
        final Path webRoot = temporaryDirectory.resolve("webapp");
        final Path webInf = webRoot.resolve("WEB-INF");
        final Path scratchDirectory = temporaryDirectory.resolve("scratch");
        Files.createDirectories(webInf);
        Files.createDirectories(scratchDirectory);
        Files.writeString(webRoot.resolve("index.jsp"), """
                <%@ taglib uri=\"/WEB-INF/functions.tld\" prefix=\"coverage\" %>
                ${coverage:combine('left', 'right')}
                """, StandardCharsets.UTF_8);
        Files.writeString(webInf.resolve("functions.tld"), """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <taglib>
                  <tlib-version>1.0</tlib-version>
                  <jsp-version>2.0</jsp-version>
                  <short-name>coverage</short-name>
                  <function>
                    <name>combine</name>
                    <function-class>%s</function-class>
                    <function-signature>%s</function-signature>
                  </function>
                </taglib>
                """.formatted(
                Functions.class.getName(),
                "java.lang.String combine(java.lang.String, java.lang.String)"), StandardCharsets.UTF_8);

        final FileSystemServletContext servletContext = new FileSystemServletContext(webRoot, scratchDirectory);
        final TestOptions options = new TestOptions(scratchDirectory.toFile(), servletContext);
        final TestCompilationContext context = new TestCompilationContext(
                "/index.jsp",
                options,
                servletContext,
                ValidatorAnonymous1MapperELVisitorTest.class.getClassLoader());
        final TestCompiler compiler = new TestCompiler();
        context.setCompiler(compiler);
        compiler.init(context, null);

        compiler.compile(false);

        final Path generatedServletPath = Path.of(context.getServletJavaFileName());
        final String generatedServlet = Files.readString(generatedServletPath, StandardCharsets.UTF_8);
        assertThat(generatedServlet).contains(Functions.class.getName(), "combine");
    }

    public static final class Functions {
        private Functions() {
        }

        public static String combine(String left, String right) {
            return left + right;
        }
    }

    private static final class TestCompiler extends Compiler {
        @Override
        protected void generateClass(String[] smap) throws FileNotFoundException, JasperException, Exception {
            // This test exercises translation-time validation only.
        }
    }

    private static final class TestCompilationContext extends JspCompilationContext {
        private final ClassLoader classLoader;
        private Compiler compiler;

        private TestCompilationContext(
                String jspUri,
                Options options,
                ServletContext context,
                ClassLoader classLoader) {
            super(jspUri, false, options, context, null, null);
            this.classLoader = classLoader;
        }

        private void setCompiler(Compiler compiler) {
            this.compiler = compiler;
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }

        @Override
        public String getClassPath() {
            return System.getProperty("java.class.path");
        }

        @Override
        public Compiler createCompiler() {
            return compiler;
        }

        @Override
        public String[] getTldLocation(String uri) {
            return new String[] {uri, null};
        }
    }

    private static final class TestOptions implements Options {
        private final File scratchDirectory;
        private final JspConfig jspConfig;
        private final TagPluginManager tagPluginManager;
        private final Map cache = new HashMap();

        private TestOptions(File scratchDirectory, ServletContext servletContext) {
            this.scratchDirectory = scratchDirectory;
            this.jspConfig = new JspConfig(servletContext);
            this.tagPluginManager = new TagPluginManager(servletContext);
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
            return jspConfig;
        }

        @Override
        public boolean isXpoweredBy() {
            return false;
        }

        @Override
        public TagPluginManager getTagPluginManager() {
            return tagPluginManager;
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

    private static final class FileSystemServletContext implements ServletContext {
        private final Path webRoot;
        private final File scratchDirectory;

        private FileSystemServletContext(Path webRoot, Path scratchDirectory) {
            this.webRoot = webRoot;
            this.scratchDirectory = scratchDirectory.toFile();
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
            final Path resource = resolve(path);
            if (Files.exists(resource)) {
                return resource.toUri().toURL();
            }
            return null;
        }

        @Override
        public InputStream getResourceAsStream(String path) {
            try {
                final Path resource = resolve(path);
                if (Files.exists(resource)) {
                    return Files.newInputStream(resource);
                }
                return null;
            } catch (IOException exception) {
                return null;
            }
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
            // The test context does not retain log messages.
        }

        @Override
        public void log(Exception exception, String msg) {
            // The test context does not retain log messages.
        }

        @Override
        public void log(String message, Throwable throwable) {
            // The test context does not retain log messages.
        }

        @Override
        public String getRealPath(String path) {
            return resolve(path).toString();
        }

        @Override
        public String getServerInfo() {
            return "file-system-servlet-context";
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
            if ("javax.servlet.context.tempdir".equals(name)) {
                return scratchDirectory;
            }
            return null;
        }

        @Override
        public Enumeration getAttributeNames() {
            return Collections.enumeration(Collections.singleton("javax.servlet.context.tempdir"));
        }

        @Override
        public void setAttribute(String name, Object object) {
            // The test context exposes only the temporary directory attribute.
        }

        @Override
        public void removeAttribute(String name) {
            // The test context exposes only the temporary directory attribute.
        }

        @Override
        public String getServletContextName() {
            return "coverage";
        }

        private Path resolve(String path) {
            final String normalizedPath;
            if (path.startsWith("/")) {
                normalizedPath = path.substring(1);
            } else {
                normalizedPath = path;
            }
            return webRoot.resolve(normalizedPath).normalize();
        }
    }
}
