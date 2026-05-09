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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.jasper.EmbeddedServletOptions;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.compiler.Compiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BeanRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void getPropertyForTypedUseBeanResolvesBeanClassDuringGeneration() throws Exception {
        final Path webRoot = temporaryDirectory.resolve("webapp");
        final Path scratchDirectory = temporaryDirectory.resolve("work");
        Files.createDirectories(webRoot);
        Files.createDirectories(scratchDirectory);
        Files.writeString(webRoot.resolve("index.jsp"), """
                <jsp:useBean id="locale" type="java.util.Locale" scope="page" />
                <jsp:getProperty name="locale" property="language" />
                """, StandardCharsets.UTF_8);

        final MinimalServletContext servletContext = new MinimalServletContext(webRoot, scratchDirectory.toFile());
        final EmbeddedServletOptions options = new EmbeddedServletOptions(
                new MapBackedServletConfig(jasperParameters(scratchDirectory), servletContext),
                servletContext);
        final JspCompilationContext context = new JspCompilationContext(
                "/index.jsp",
                false,
                options,
                servletContext,
                null,
                null);

        try (CountingClassLoader classLoader = new CountingClassLoader(BeanRepositoryTest.class.getClassLoader())) {
            context.setClassLoader(classLoader);
            final NoClassGenerationCompiler compiler = new NoClassGenerationCompiler();
            compiler.init(context, null);

            compiler.compile(false);

            assertThat(classLoader.localeLoads).isGreaterThan(0);
            assertThat(Files.readString(new File(context.getServletJavaFileName()).toPath(), StandardCharsets.UTF_8))
                    .contains(".getLanguage()")
                    .contains("java.util.Locale");
        }
    }

    private static Map<String, String> jasperParameters(Path scratchDirectory) {
        final Map<String, String> initParameters = new LinkedHashMap<>();
        initParameters.put("scratchdir", scratchDirectory.toString());
        initParameters.put("keepgenerated", "true");
        initParameters.put("mappedfile", "false");
        initParameters.put("suppressSmap", "true");
        initParameters.put("javaEncoding", StandardCharsets.UTF_8.name());
        return initParameters;
    }

    private static final class CountingClassLoader extends URLClassLoader {
        private int localeLoads;

        private CountingClassLoader(ClassLoader parent) {
            super(new URL[0], parent);
        }

        @Override
        public Class loadClass(String name) throws ClassNotFoundException {
            if ("java.util.Locale".equals(name)) {
                localeLoads++;
            }
            return super.loadClass(name);
        }
    }

    private static final class NoClassGenerationCompiler extends Compiler {
        @Override
        protected void generateClass(String[] smap) throws FileNotFoundException, JasperException, Exception {
            // This test only needs Java generation, where BeanRepository resolves bean types.
        }
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
        private final Path webRoot;
        private final File temporaryDirectory;

        private MinimalServletContext(Path webRoot, File temporaryDirectory) {
            this.webRoot = webRoot;
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
            final Path directory = resolvePath(path);
            if (!Files.isDirectory(directory)) {
                return Collections.emptySet();
            }
            final Set<String> resources = new LinkedHashSet<>();
            try (Stream<Path> children = Files.list(directory)) {
                children.forEach(child -> {
                    final String resourceName = child.getFileName().toString() + (Files.isDirectory(child) ? "/" : "");
                    resources.add(path.endsWith("/") ? path + resourceName : path + "/" + resourceName);
                });
            } catch (IOException exception) {
                return Collections.emptySet();
            }
            return resources;
        }

        @Override
        public URL getResource(String path) throws MalformedURLException {
            final Path resource = resolvePath(path);
            if (!Files.exists(resource)) {
                return null;
            }
            return resource.toUri().toURL();
        }

        @Override
        public InputStream getResourceAsStream(String path) {
            final Path resource = resolvePath(path);
            if (!Files.isRegularFile(resource)) {
                return null;
            }
            try {
                return Files.newInputStream(resource);
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
            return resolvePath(path).toString();
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

        private Path resolvePath(String path) {
            final String relativePath = path.replaceFirst("^/", "");
            return webRoot.resolve(relativePath).normalize();
        }
    }
}
