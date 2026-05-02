/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.aspectj.weaver.loadtime.ClassLoaderWeavingAdaptor;
import org.aspectj.weaver.loadtime.IWeavingContext;
import org.aspectj.weaver.loadtime.definition.Definition;
import org.aspectj.weaver.tools.WeavingAdaptor;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderWeavingAdaptorTest {
    private static final String LINT_RESOURCE = "aspectj-ltw-lint.properties";

    @Test
    void initializesLoadTimeWeaverWithLintResourceAndGeneratedConcreteAspect() {
        ResourceTrackingClassLoader loader = new ResourceTrackingClassLoader(
                ClassLoaderWeavingAdaptorTest.class.getClassLoader(),
                LINT_RESOURCE,
                "adviceDidNotMatch=ignore\n"
        );
        Definition definition = concreteAspectDefinition();
        ClassLoaderWeavingAdaptor adaptor = new ClassLoaderWeavingAdaptor();

        boolean completed = false;
        try {
            adaptor.initialize(loader, new StaticWeavingContext(loader, definition));
            completed = true;
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }

        assertThat(loader.resourceRequestCount()).isEqualTo(1);
        if (completed) {
            assertThat(adaptor.getMessageHolder()).isNotNull();
        }
    }

    private static Definition concreteAspectDefinition() {
        Definition definition = new Definition();
        definition.appendWeaverOptions("-Xlintfile:" + LINT_RESOURCE);
        definition.getConcreteAspects().add(new Definition.ConcreteAspect(
                "org_aspectj.aspectjweaver.generated.LoadTimeConcreteAspect",
                null,
                "org_aspectj.aspectjweaver..*",
                null
        ));
        return definition;
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
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
