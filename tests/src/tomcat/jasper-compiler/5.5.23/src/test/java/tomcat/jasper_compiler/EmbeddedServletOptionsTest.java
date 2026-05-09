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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.jasper.EmbeddedServletOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class EmbeddedServletOptionsTest {
    @TempDir
    Path scratchDirectory;

    @Test
    void constructorReadsServletInitParameters() {
        final Map<String, String> initParameters = new LinkedHashMap<>();
        initParameters.put("scratchdir", scratchDirectory.toString());
        initParameters.put("keepgenerated", "false");
        initParameters.put("trimSpaces", "true");
        initParameters.put("enablePooling", "false");
        initParameters.put("mappedfile", "false");
        initParameters.put("sendErrToClient", "true");
        initParameters.put("classdebuginfo", "false");
        initParameters.put("checkInterval", "7");
        initParameters.put("modificationTestInterval", "11");
        initParameters.put("development", "false");
        initParameters.put("suppressSmap", "true");
        initParameters.put("dumpSmap", "true");
        initParameters.put("genStrAsCharArray", "true");
        initParameters.put("errorOnUseBeanInvalidClassAttribute", "false");
        initParameters.put("ieClassId", "test-ie-class-id");
        initParameters.put("classpath", "test-classpath");
        initParameters.put("compiler", "javac");
        initParameters.put("compilerTargetVM", "1.6");
        initParameters.put("compilerSourceVM", "1.6");
        initParameters.put("javaEncoding", "UTF-8");
        initParameters.put("fork", "false");
        initParameters.put("xpoweredBy", "true");

        final ServletContext servletContext = new MinimalServletContext(scratchDirectory.toFile());
        final ServletConfig servletConfig = new MapBackedServletConfig(
                initParameters,
                servletContext);
        final EmbeddedServletOptions options = new EmbeddedServletOptions(
                servletConfig,
                servletContext);

        assertThat(options.getScratchDir()).isEqualTo(scratchDirectory.toFile());
        assertThat(options.getKeepGenerated()).isFalse();
        assertThat(options.getTrimSpaces()).isTrue();
        assertThat(options.isPoolingEnabled()).isFalse();
        assertThat(options.getMappedFile()).isFalse();
        assertThat(options.getSendErrorToClient()).isTrue();
        assertThat(options.getClassDebugInfo()).isFalse();
        assertThat(options.getCheckInterval()).isEqualTo(7);
        assertThat(options.getModificationTestInterval()).isEqualTo(11);
        assertThat(options.getDevelopment()).isFalse();
        assertThat(options.isSmapSuppressed()).isTrue();
        assertThat(options.isSmapDumped()).isTrue();
        assertThat(options.genStringAsCharArray()).isTrue();
        assertThat(options.getErrorOnUseBeanInvalidClassAttribute()).isFalse();
        assertThat(options.getIeClassId()).isEqualTo("test-ie-class-id");
        assertThat(options.getClassPath()).isEqualTo("test-classpath");
        assertThat(options.getCompiler()).isEqualTo("javac");
        assertThat(options.getCompilerTargetVM()).isEqualTo("1.6");
        assertThat(options.getCompilerSourceVM()).isEqualTo("1.6");
        assertThat(options.getJavaEncoding()).isEqualTo("UTF-8");
        assertThat(options.getFork()).isFalse();
        assertThat(options.isXpoweredBy()).isTrue();
        assertThat(options.getTldLocationsCache()).isNotNull();
        assertThat(options.getJspConfig()).isNotNull();
        assertThat(options.getTagPluginManager()).isNotNull();
    }

    private static final class MapBackedServletConfig implements ServletConfig {
        private final Map<String, String> initParameters;
        private final ServletContext servletContext;

        private MapBackedServletConfig(
                Map<String, String> initParameters,
                ServletContext servletContext) {
            this.initParameters = initParameters;
            this.servletContext = servletContext;
        }

        @Override
        public String getServletName() {
            return "jsp";
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
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

    private static final class MinimalServletContext implements ServletContext {
        private final File temporaryDirectory;

        private MinimalServletContext(File temporaryDirectory) {
            this.temporaryDirectory = temporaryDirectory;
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
            return null;
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
            if ("javax.servlet.context.tempdir".equals(name)) {
                return temporaryDirectory;
            }
            return null;
        }

        @Override
        public Enumeration getAttributeNames() {
            return Collections.enumeration(Collections.singleton("javax.servlet.context.tempdir"));
        }

        @Override
        public void setAttribute(String name, Object object) {
            // The minimal context exposes only the temporary directory attribute.
        }

        @Override
        public void removeAttribute(String name) {
            // The minimal context exposes only the temporary directory attribute.
        }

        @Override
        public String getServletContextName() {
            return "minimal";
        }
    }
}
