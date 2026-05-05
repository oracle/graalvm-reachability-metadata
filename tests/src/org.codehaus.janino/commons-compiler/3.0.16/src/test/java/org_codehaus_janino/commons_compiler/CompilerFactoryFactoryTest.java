/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

import org.codehaus.commons.compiler.AbstractCompilerFactory;
import org.codehaus.commons.compiler.AbstractJavaSourceClassLoader;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.ICompiler;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompilerFactoryFactoryTest {
    private static final String PROPERTIES_RESOURCE_NAME = "org.codehaus.commons.compiler.properties";
    private static final String TEST_FACTORY_ID = "test-compiler-factory";
    private static final byte[] FACTORY_CONFIGURATION = ("compilerFactory="
            + TestCompilerFactory.class.getName() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);

    @Test
    void discoversCompilerFactoriesFromContextClassLoaderResources() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader resourceClassLoader = new ResourceBackedClassLoader(originalClassLoader);

        try {
            Thread.currentThread().setContextClassLoader(resourceClassLoader);

            ICompilerFactory namedFactory = CompilerFactoryFactory.getCompilerFactory(
                    TestCompilerFactory.class.getName());
            ICompilerFactory[] discoveredFactories = CompilerFactoryFactory.getAllCompilerFactories();
            ICompilerFactory defaultFactory = CompilerFactoryFactory.getDefaultCompilerFactory();

            assertThat(namedFactory).isInstanceOf(TestCompilerFactory.class);
            assertThat(discoveredFactories).hasSize(1);
            assertThat(discoveredFactories[0]).isInstanceOf(TestCompilerFactory.class);
            assertThat(defaultFactory).isInstanceOf(TestCompilerFactory.class);
            assertThat(defaultFactory.getId()).isEqualTo(TEST_FACTORY_ID);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public static final class TestCompilerFactory extends AbstractCompilerFactory {
        public TestCompilerFactory() {
        }

        @Override
        public String getId() {
            return TEST_FACTORY_ID;
        }

        @Override
        public String toString() {
            return "test compiler factory";
        }

        @Override
        public String getImplementationVersion() {
            return "test";
        }

        @Override
        public ICompiler newCompiler() {
            throw new UnsupportedOperationException(getId() + ": newCompiler");
        }

        @Override
        public AbstractJavaSourceClassLoader newJavaSourceClassLoader() {
            return AbstractJavaSourceClassLoaderTest.newJavaSourceClassLoader();
        }

        @Override
        public AbstractJavaSourceClassLoader newJavaSourceClassLoader(ClassLoader parentClassLoader) {
            return AbstractJavaSourceClassLoaderTest.newJavaSourceClassLoader(parentClassLoader);
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
            return new URL(null, "memory:commons-compiler-factory", new URLStreamHandler() {
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
