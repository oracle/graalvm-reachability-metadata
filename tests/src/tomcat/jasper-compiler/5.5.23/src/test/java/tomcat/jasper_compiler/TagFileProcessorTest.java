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
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.SimpleTagSupport;
import javax.servlet.jsp.tagext.TagInfo;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.Compiler;
import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldLocationsCache;
import org.apache.jasper.runtime.JspSourceDependent;
import org.apache.jasper.servlet.JspServletWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TagFileProcessorTest {
    private static final String TAG_FILE_PATH = "/WEB-INF/tags/dependency.tag";

    private JspRuntimeContext runtimeContext;
    private JspServletWrapper wrapper;
    private URLClassLoader runtimeParentLoader;

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void closeRuntimeResources() throws Exception {
        try {
            if (wrapper != null) {
                wrapper.destroy();
            }
            if (runtimeContext != null) {
                runtimeContext.destroy();
            }
        } finally {
            if (runtimeParentLoader != null) {
                runtimeParentLoader.close();
            }
        }
    }

    @Test
    void loadTagFilesInstantiatesLoadedTagHandlerToCollectDependants() throws Exception {
        RecordingDependentTag.constructorCalls.set(0);

        final Path webRoot = temporaryDirectory.resolve("webapp");
        final Path tagDirectory = webRoot.resolve("WEB-INF/tags");
        final Path scratchDirectory = temporaryDirectory.resolve("scratch");
        Files.createDirectories(tagDirectory);
        Files.createDirectories(scratchDirectory);
        Files.writeString(webRoot.resolve("index.jsp"), """
                <%@ taglib prefix=\"tags\" tagdir=\"/WEB-INF/tags\" %>
                <tags:dependency />
                """, StandardCharsets.UTF_8);
        Files.writeString(tagDirectory.resolve("dependency.tag"), """
                <%@ tag body-content=\"empty\" %>
                """, StandardCharsets.UTF_8);

        final FileSystemServletContext servletContext = new FileSystemServletContext(webRoot, scratchDirectory);
        final TestOptions options = new TestOptions(scratchDirectory.toFile(), servletContext);
        runtimeContext = newRuntimeContext(servletContext, options);
        wrapper = new TrackingTagFileWrapper(
                servletContext,
                options,
                TAG_FILE_PATH,
                tagInfo(),
                runtimeContext,
                RecordingDependentTag.class);
        runtimeContext.addWrapper(TAG_FILE_PATH, wrapper);

        final TestCompilationContext context = new TestCompilationContext(
                "/index.jsp",
                options,
                servletContext,
                runtimeContext,
                TagFileProcessorTest.class.getClassLoader());
        final TestCompiler compiler = new TestCompiler();
        compiler.init(context, null);

        compiler.compile(false);

        assertThat(((TrackingTagFileWrapper) wrapper).loadCalls.get()).isEqualTo(1);
        assertThat(RecordingDependentTag.constructorCalls.get()).isEqualTo(1);
    }

    private JspRuntimeContext newRuntimeContext(ServletContext servletContext, Options options) throws Exception {
        final ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader parentClassLoader = originalContextClassLoader == null
                ? TagFileProcessorTest.class.getClassLoader()
                : originalContextClassLoader;
        runtimeParentLoader = new URLClassLoader(new URL[0], parentClassLoader);
        Thread.currentThread().setContextClassLoader(runtimeParentLoader);
        try {
            return new JspRuntimeContext(servletContext, options);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static TagInfo tagInfo() {
        return new TagInfo(
                "dependency",
                RecordingDependentTag.class.getName(),
                "empty",
                "dependency tag",
                null,
                null,
                null);
    }

    public static final class RecordingDependentTag extends SimpleTagSupport implements JspSourceDependent {
        private static final AtomicInteger constructorCalls = new AtomicInteger();

        public RecordingDependentTag() {
            constructorCalls.incrementAndGet();
        }

        @Override
        public void doTag() throws JspException, IOException {
            // The test compiles the page but does not execute the tag body.
        }

        @Override
        public Object getDependants() {
            return Collections.singletonList("/WEB-INF/tags/nested.tag");
        }
    }

    private static final class TrackingTagFileWrapper extends JspServletWrapper {
        private final AtomicInteger loadCalls = new AtomicInteger();
        private final Class<?> tagHandlerClass;

        private TrackingTagFileWrapper(
                ServletContext servletContext,
                Options options,
                String tagFilePath,
                TagInfo tagInfo,
                JspRuntimeContext runtimeContext,
                Class<?> tagHandlerClass) throws JasperException {
            super(servletContext, options, tagFilePath, tagInfo, runtimeContext, null);
            this.tagHandlerClass = tagHandlerClass;
        }

        @Override
        public Class loadTagFile() {
            loadCalls.incrementAndGet();
            return tagHandlerClass;
        }
    }

    private static final class TestCompiler extends Compiler {
        @Override
        protected void generateClass(String[] smap) throws FileNotFoundException, JasperException, Exception {
            // The test only needs translation-time tag-file loading.
        }
    }

    private static final class TestCompilationContext extends JspCompilationContext {
        private final ClassLoader classLoader;

        private TestCompilationContext(
                String jspUri,
                Options options,
                ServletContext context,
                JspRuntimeContext runtimeContext,
                ClassLoader classLoader) {
            super(jspUri, false, options, context, null, runtimeContext);
            this.classLoader = classLoader;
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }

        @Override
        public String getClassPath() {
            return System.getProperty("java.class.path");
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
            final Path directory = resolve(path);
            if (!Files.isDirectory(directory)) {
                return Collections.emptySet();
            }
            final Set<String> paths = new LinkedHashSet<>();
            try (Stream<Path> stream = Files.list(directory)) {
                stream.forEach(child -> paths.add(toContextPath(child, Files.isDirectory(child))));
            } catch (IOException exception) {
                return Collections.emptySet();
            }
            return paths;
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

        private String toContextPath(Path child, boolean directory) {
            final String relativePath = webRoot.relativize(child).toString().replace(File.separatorChar, '/');
            return "/" + relativePath + (directory ? "/" : "");
        }
    }
}
