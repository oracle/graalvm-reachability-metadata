/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.aspectj.weaver.loadtime.ClassLoaderWeavingAdaptor;
import org.aspectj.weaver.loadtime.IWeavingContext;
import org.aspectj.weaver.loadtime.definition.Definition;
import org.aspectj.weaver.tools.WeavingAdaptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderWeavingAdaptorTest {
    private static final String LINT_RESOURCE = "aspectj-ltw-lint.properties";

    @Test
    void initializesLoadTimeWeaverWithLintResource() {
        ResourceTrackingClassLoader loader = new ResourceTrackingClassLoader(
                ClassLoaderWeavingAdaptorTest.class.getClassLoader(),
                LINT_RESOURCE,
                "adviceDidNotMatch=ignore\n"
        );
        Definition definition = new Definition();
        definition.appendWeaverOptions("-Xlintfile:" + LINT_RESOURCE);
        ClassLoaderWeavingAdaptor adaptor = new ClassLoaderWeavingAdaptor();

        adaptor.initialize(loader, new StaticWeavingContext(loader, definition));

        assertThat(loader.resourceRequestCount()).isEqualTo(1);
        assertThat(adaptor.getMessageHolder()).isNotNull();
    }

    @Test
    void createsMethodHandleFromNamedMethod() throws Throwable {
        MethodHandle startsWith = ClassLoaderWeavingAdaptor.createMethodHandle(
                "java.lang.String",
                "startsWith",
                String.class
        );

        boolean matches = (boolean) startsWith.invokeExact("aspectj", "aspect");
        assertThat(matches).isTrue();
    }

    private static final class StaticWeavingContext implements IWeavingContext {
        private final ClassLoader classLoader;
        private final List<Definition> definitions;

        private StaticWeavingContext(ClassLoader classLoader, Definition definition) {
            this.classLoader = classLoader;
            this.definitions = Collections.singletonList(definition);
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            return Collections.emptyEnumeration();
        }

        @Override
        public String getBundleIdFromURL(URL url) {
            return null;
        }

        @Override
        public String getClassLoaderName() {
            return "class-loader-weaving-adaptor-test";
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
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
        public boolean isLocallyDefined(String classname) {
            return classname.startsWith("org_aspectj.aspectjweaver");
        }

        @Override
        public List<Definition> getDefinitions(ClassLoader loader, WeavingAdaptor adaptor) {
            return definitions;
        }
    }

    private static final class ResourceTrackingClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] contents;
        private int resourceRequestCount;

        private ResourceTrackingClassLoader(ClassLoader parent, String resourceName, String contents) {
            super(parent);
            this.resourceName = resourceName;
            this.contents = contents.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (resourceName.equals(name)) {
                resourceRequestCount++;
                return new ByteArrayInputStream(contents);
            }
            return super.getResourceAsStream(name);
        }

        private int resourceRequestCount() {
            return resourceRequestCount;
        }
    }
}
