/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api;

import java.util.Collections;

import javax.xml.bind.JAXBContext;
import javax_xml_bind.jaxb_api.support.InterfaceContextFactory;
import javax_xml_bind.jaxb_api.support.StubJaxbContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleUtilTest {
    private final String previousFactoryProperty = System.getProperty(JAXBContext.JAXB_CONTEXT_FACTORY);

    @AfterEach
    public void restoreFactoryProperty() {
        if (previousFactoryProperty == null) {
            System.clearProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
            return;
        }
        System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY, previousFactoryProperty);
    }

    @Test
    public void resolvesObjectFactoryClassesFromContextPathWithoutPackageProperties() throws Exception {
        JAXBContext context = withFactoryProperty(() -> JAXBContext.newInstance(
                "javax_xml_bind.jaxb_api.moduleutil.objectfactoryonly",
                new PassiveDelegatingClassLoader(getClass().getClassLoader()),
                Collections.emptyMap()));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("interface-context-factory-string");
    }

    @Test
    public void resolvesIndexedClassesFromContextPathWithoutPackageProperties() throws Exception {
        JAXBContext context = withFactoryProperty(() -> JAXBContext.newInstance(
                "javax_xml_bind.jaxb_api.moduleutil.indexonly",
                new PassiveDelegatingClassLoader(getClass().getClassLoader()),
                Collections.emptyMap()));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("interface-context-factory-string");
    }

    @Test
    public void resolvesMixedContextPathPackagesWithoutPackageProperties() throws Exception {
        JAXBContext context = withFactoryProperty(() -> JAXBContext.newInstance(
                "javax_xml_bind.jaxb_api.moduleutil.objectfactoryonly:javax_xml_bind.jaxb_api.moduleutil.indexonly",
                new PassiveDelegatingClassLoader(getClass().getClassLoader()),
                Collections.emptyMap()));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("interface-context-factory-string");
    }

    @Test
    public void resolvesIndexedClassesFromContextClassLoaderWithoutPackageProperties() throws Exception {
        JAXBContext context = withFactoryPropertyAndContextClassLoader(
                new PassiveDelegatingClassLoader(getClass().getClassLoader()),
                () -> JAXBContext.newInstance("javax_xml_bind.jaxb_api.moduleutil.indexonly"));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("interface-context-factory-string");
    }

    private static JAXBContext withFactoryProperty(ThrowingSupplier<JAXBContext> supplier) throws Exception {
        System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY, InterfaceContextFactory.class.getName());
        return supplier.get();
    }

    private static JAXBContext withFactoryPropertyAndContextClassLoader(
            ClassLoader classLoader,
            ThrowingSupplier<JAXBContext> supplier) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            return withFactoryProperty(supplier);
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class PassiveDelegatingClassLoader extends ClassLoader {
        private PassiveDelegatingClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
