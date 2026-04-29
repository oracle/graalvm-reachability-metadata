/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.aspectj.weaver.loadtime.ClassLoaderWeavingAdaptor;
import org.aspectj.weaver.loadtime.IWeavingContext;
import org.aspectj.weaver.loadtime.definition.Definition;
import org.aspectj.weaver.tools.WeavingAdaptor;
import org.junit.jupiter.api.Test;

public class ClassLoaderWeavingAdaptorTest {
    private static final String GENERATED_ASPECT_NAME = "org_aspectj.aspectjweaver.GeneratedConcreteAspect";
    private static final String LINT_RESOURCE = "org_aspectj/aspectjweaver/class-loader-weaving-adaptor-lint.properties";

    @Test
    void initializesWithConcreteAspectAndLintResource() {
        Definition definition = new Definition();
        definition.appendWeaverOptions("-Xlintfile:" + LINT_RESOURCE);
        definition.getConcreteAspects().add(concreteAspect());

        ClassLoader loader = new TestClassLoader(ClassLoaderWeavingAdaptorTest.class.getClassLoader());
        ClassLoaderWeavingAdaptor adaptor = new ClassLoaderWeavingAdaptor();

        adaptor.initialize(loader, new StaticDefinitionsWeavingContext(loader, List.of(definition)));

        assertThat(adaptor.getNamespace()).contains(GENERATED_ASPECT_NAME);
    }

    private static Definition.ConcreteAspect concreteAspect() {
        return new Definition.ConcreteAspect(GENERATED_ASPECT_NAME, Object.class.getName(), null, "persingleton()");
    }

    private static class TestClassLoader extends ClassLoader {
        TestClassLoader(ClassLoader parent) {
            super(parent);
        }
    }

    private static class StaticDefinitionsWeavingContext implements IWeavingContext {
        private final ClassLoader loader;
        private final List<Definition> definitions;

        StaticDefinitionsWeavingContext(ClassLoader loader, List<Definition> definitions) {
            this.loader = loader;
            this.definitions = definitions;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return loader.getResources(name);
        }

        @Override
        public String getBundleIdFromURL(URL url) {
            return url.toExternalForm();
        }

        @Override
        public String getClassLoaderName() {
            return loader.getClass().getName();
        }

        @Override
        public ClassLoader getClassLoader() {
            return loader;
        }

        @Override
        public String getFile(URL url) {
            return url.toExternalForm();
        }

        @Override
        public String getId() {
            return "class-loader-weaving-adaptor-test";
        }

        @Override
        public boolean isLocallyDefined(String className) {
            return className.startsWith("org_aspectj.aspectjweaver.");
        }

        @Override
        public List<Definition> getDefinitions(ClassLoader classLoader, WeavingAdaptor adaptor) {
            return Collections.unmodifiableList(definitions);
        }
    }
}
