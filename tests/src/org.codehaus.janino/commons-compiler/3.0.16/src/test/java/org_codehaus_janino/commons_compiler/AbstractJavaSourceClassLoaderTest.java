/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;

import org.codehaus.commons.compiler.AbstractJavaSourceClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractJavaSourceClassLoaderTest {
    private static final String PROPERTIES_RESOURCE_NAME = "org.codehaus.commons.compiler.properties";
    private static final byte[] FACTORY_CONFIGURATION = ("compilerFactory="
            + CompilerFactoryFactoryTest.TestCompilerFactory.class.getName() + System.lineSeparator()).getBytes(
                    StandardCharsets.UTF_8);

    @TempDir
    Path temporaryDirectory;

    private static RecordingJavaSourceClassLoader lastCreatedClassLoader;

    @Test
    void mainLoadsTargetClassAndInvokesItsMainMethod() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader resourceClassLoader = new ResourceBackedClassLoader(originalClassLoader);
        String sourcePath = temporaryDirectory.toString();

        FixtureMain.receivedArguments = null;
        lastCreatedClassLoader = null;

        try {
            Thread.currentThread().setContextClassLoader(resourceClassLoader);

            AbstractJavaSourceClassLoader.main(new String[] {
                    "-sourcepath", sourcePath,
                    "-encoding", StandardCharsets.UTF_8.name(),
                    "-g",
                    FixtureMain.class.getName(),
                    "alpha", "beta"
            });
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(lastCreatedClassLoader).isNotNull();
        assertThat(lastCreatedClassLoader.requestedClassName).isEqualTo(FixtureMain.class.getName());
        assertThat(lastCreatedClassLoader.sourcePath).containsExactly(new File(sourcePath));
        assertThat(lastCreatedClassLoader.characterEncoding).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(lastCreatedClassLoader.debuggingInfoLines).isTrue();
        assertThat(lastCreatedClassLoader.debuggingInfoVars).isTrue();
        assertThat(lastCreatedClassLoader.debuggingInfoSource).isTrue();
        assertThat(FixtureMain.receivedArguments).containsExactly("alpha", "beta");
    }

    static AbstractJavaSourceClassLoader newJavaSourceClassLoader() {
        return newJavaSourceClassLoader(AbstractJavaSourceClassLoaderTest.class.getClassLoader());
    }

    static AbstractJavaSourceClassLoader newJavaSourceClassLoader(ClassLoader parentClassLoader) {
        lastCreatedClassLoader = new RecordingJavaSourceClassLoader(parentClassLoader);
        return lastCreatedClassLoader;
    }

    public static final class FixtureMain {
        private static String[] receivedArguments;

        public static void main(String[] args) {
            receivedArguments = args.clone();
        }
    }

    private static final class RecordingJavaSourceClassLoader extends AbstractJavaSourceClassLoader {
        private File[] sourcePath;
        private String characterEncoding;
        private boolean debuggingInfoLines;
        private boolean debuggingInfoVars;
        private boolean debuggingInfoSource;
        private String requestedClassName;

        private RecordingJavaSourceClassLoader(ClassLoader parentClassLoader) {
            super(parentClassLoader);
        }

        @Override
        public void setSourcePath(File[] sourcePath) {
            this.sourcePath = sourcePath.clone();
        }

        @Override
        public void setSourceFileCharacterEncoding(String optionalCharacterEncoding) {
            this.characterEncoding = optionalCharacterEncoding;
        }

        @Override
        public void setDebuggingInfo(boolean lines, boolean vars, boolean source) {
            this.debuggingInfoLines = lines;
            this.debuggingInfoVars = vars;
            this.debuggingInfoSource = source;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            requestedClassName = name;
            if (FixtureMain.class.getName().equals(name)) {
                return FixtureMain.class;
            }
            return super.loadClass(name);
        }
    }

    private static final class ResourceBackedClassLoader extends ClassLoader {
        private ResourceBackedClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!PROPERTIES_RESOURCE_NAME.equals(name)) {
                return super.getResourceAsStream(name);
            }
            return new ByteArrayInputStream(FACTORY_CONFIGURATION);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (!PROPERTIES_RESOURCE_NAME.equals(name)) {
                return super.getResources(name);
            }
            return Collections.enumeration(Collections.singletonList(resourceUrl()));
        }

        private URL resourceUrl() throws IOException {
            return new URL(null, "memory:java-source-class-loader-factory", new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() {
                            connected = true;
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(FACTORY_CONFIGURATION);
                        }
                    };
                }
            });
        }
    }
}
