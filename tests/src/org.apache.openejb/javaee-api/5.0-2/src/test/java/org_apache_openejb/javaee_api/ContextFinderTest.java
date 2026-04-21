/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_openejb.javaee_api;

import java.util.Collections;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.openejb.javaee.api.servicebound.ServiceBoundType;
import org.apache.openejb.javaee.api.servicebound.WrongTypeBoundType;
import org.apache.openejb.javaee.api.support.StubJaxbContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ContextFinderTest {
    private static final String CONTEXT_FACTORY_SYSTEM_PROPERTY = JAXBContext.class.getName();
    private static final String PROPERTIES_CONTEXT_PATH = "org_apache_openejb.javaee_api.propertiespath";
    private static final String SERVICE_CONTEXT_PATH = "org_apache_openejb.javaee_api.servicepath";
    private static final String SYSTEM_PROPERTY_CONTEXT_PATH = "org_apache_openejb.javaee_api.systemlookup";

    private String originalContextFactorySystemProperty;

    @BeforeEach
    public void rememberContextFactorySystemProperty() {
        originalContextFactorySystemProperty = System.getProperty(CONTEXT_FACTORY_SYSTEM_PROPERTY);
    }

    @AfterEach
    public void restoreContextFactorySystemProperty() {
        restoreSystemProperty(originalContextFactorySystemProperty);
    }

    @Test
    public void loadsThreeArgumentFactoryFromJaxbProperties() throws Exception {
        clearContextFactorySystemProperty();

        JAXBContext context = JAXBContext.newInstance(
                PROPERTIES_CONTEXT_PATH,
                getClass().getClassLoader(),
                Collections.singletonMap("trigger", "jaxb-properties"));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("three-argument-factory");
    }

    @Test
    public void loadsThreeArgumentFactoryFromSystemPropertyWithoutExplicitClassLoader() throws Exception {
        System.setProperty(
                CONTEXT_FACTORY_SYSTEM_PROPERTY,
                "org.apache.openejb.javaee.api.support.ThreeArgumentContextFactory");

        JAXBContext context = JAXBContext.newInstance(
                SYSTEM_PROPERTY_CONTEXT_PATH,
                null,
                Collections.singletonMap("trigger", "system-property"));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("three-argument-factory");
    }

    @Test
    public void loadsServiceProviderForContextPathUsingFallbackFactoryMethod() throws Exception {
        clearContextFactorySystemProperty();

        JAXBContext context = JAXBContext.newInstance(
                SERVICE_CONTEXT_PATH,
                getClass().getClassLoader(),
                Collections.emptyMap());

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("service-context-factory-string");
    }

    @Test
    public void loadsServiceProviderForBoundClassesWithContextClassLoader() throws Exception {
        clearContextFactorySystemProperty();

        JAXBContext context = withContextClassLoader(getClass().getClassLoader(),
                () -> JAXBContext.newInstance(ServiceBoundType.class));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("service-context-factory-classes");
    }

    @Test
    public void loadsServiceProviderForBoundClassesWithoutContextClassLoader() throws Exception {
        clearContextFactorySystemProperty();

        JAXBContext context = withContextClassLoader(null, () -> JAXBContext.newInstance(ServiceBoundType.class));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("service-context-factory-classes");
    }

    @Test
    public void throwsJaxbExceptionWhenProviderReturnsWrongType() throws Exception {
        clearContextFactorySystemProperty();

        assertThatThrownBy(() -> withContextClassLoader(
                getClass().getClassLoader(),
                () -> JAXBContext.newInstance(WrongTypeBoundType.class)))
                .isInstanceOf(JAXBException.class);
    }

    private static void clearContextFactorySystemProperty() {
        System.clearProperty(CONTEXT_FACTORY_SYSTEM_PROPERTY);
    }

    private static void restoreSystemProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(CONTEXT_FACTORY_SYSTEM_PROPERTY);
            return;
        }
        System.setProperty(CONTEXT_FACTORY_SYSTEM_PROPERTY, previousValue);
    }

    private static <T> T withContextClassLoader(ClassLoader classLoader, ThrowingSupplier<T> supplier) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
