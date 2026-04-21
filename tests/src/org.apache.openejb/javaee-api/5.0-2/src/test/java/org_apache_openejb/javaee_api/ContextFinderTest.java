/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_openejb.javaee_api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.Validator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ContextFinderTest {
    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalFactoryProperty = System.getProperty(JAXBContext.JAXB_CONTEXT_FACTORY);

    @AfterEach
    void restoreThreadState() {
        currentThread.setContextClassLoader(originalContextClassLoader);
        if (originalFactoryProperty == null) {
            System.clearProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
        } else {
            System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY, originalFactoryProperty);
        }
    }

    @Test
    void loadsFactoryFromSystemJaxbPropertiesUsingThreeArgumentFactoryMethod() throws JAXBException {
        System.clearProperty(JAXBContext.JAXB_CONTEXT_FACTORY);

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("origin", "system-jaxb-properties");

        JAXBContext context = JAXBContext.newInstance(
                "org_apache_openejb.javaee_api.contextpath.system",
                null,
                properties);

        RecordingJaxbContext recordingContext = (RecordingJaxbContext) context;
        assertThat(recordingContext.getCreationMode()).isEqualTo("string-map");
        assertThat(recordingContext.getContextPath()).isEqualTo("org_apache_openejb.javaee_api.contextpath.system");
        assertThat(recordingContext.getClassLoader()).isNull();
        assertThat(recordingContext.getProperties()).isSameAs(properties);
    }

    @Test
    void loadsFactoryFromPackagePropertiesUsingProvidedClassLoaderAndTwoArgumentFallback() throws JAXBException {
        System.clearProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
        ClassLoader testClassLoader = ContextFinderTest.class.getClassLoader();

        JAXBContext context = JAXBContext.newInstance(
                "org_apache_openejb.javaee_api.contextpath.loader",
                testClassLoader);

        RecordingJaxbContext recordingContext = (RecordingJaxbContext) context;
        assertThat(recordingContext.getCreationMode()).isEqualTo("string-two-arg");
        assertThat(recordingContext.getContextPath()).isEqualTo("org_apache_openejb.javaee_api.contextpath.loader");
        assertThat(recordingContext.getClassLoader()).isSameAs(testClassLoader);
        assertThat(recordingContext.getProperties()).isNull();
    }

    @Test
    void loadsFactoryFromServiceDescriptorForContextPathLookups() throws JAXBException {
        System.clearProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
        ClassLoader testClassLoader = ContextFinderTest.class.getClassLoader();
        Map<String, Object> properties = Collections.<String, Object>singletonMap("origin", "service-loader");

        JAXBContext context = JAXBContext.newInstance(
                "org_apache_openejb.javaee_api.contextpath.service",
                testClassLoader,
                properties);

        RecordingJaxbContext recordingContext = (RecordingJaxbContext) context;
        assertThat(recordingContext.getCreationMode()).isEqualTo("string-map");
        assertThat(recordingContext.getContextPath()).isEqualTo("org_apache_openejb.javaee_api.contextpath.service");
        assertThat(recordingContext.getClassLoader()).isSameAs(testClassLoader);
        assertThat(recordingContext.getProperties()).isSameAs(properties);
    }

    @Test
    void loadsFactoryFromServiceDescriptorUsingThreadContextClassLoader() throws JAXBException {
        System.clearProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
        ClassLoader testClassLoader = ContextFinderTest.class.getClassLoader();
        currentThread.setContextClassLoader(testClassLoader);

        JAXBContext context = JAXBContext.newInstance(ContextFinderTest.class);

        RecordingJaxbContext recordingContext = (RecordingJaxbContext) context;
        assertThat(recordingContext.getCreationMode()).isEqualTo("classes-map");
        assertThat(recordingContext.getBoundClasses()).containsExactly(ContextFinderTest.class);
        assertThat(recordingContext.getInvocationThreadContextClassLoader()).isSameAs(testClassLoader);
        assertThat(recordingContext.getProperties()).isEmpty();
    }

    @Test
    void loadsFactoryFromSystemServiceDescriptorWhenThreadContextClassLoaderIsNull() throws JAXBException {
        System.clearProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
        currentThread.setContextClassLoader(null);

        JAXBContext context = JAXBContext.newInstance(ContextFinderTest.class);

        RecordingJaxbContext recordingContext = (RecordingJaxbContext) context;
        assertThat(recordingContext.getCreationMode()).isEqualTo("classes-map");
        assertThat(recordingContext.getBoundClasses()).containsExactly(ContextFinderTest.class);
        assertThat(recordingContext.getInvocationThreadContextClassLoader()).isNull();
        assertThat(recordingContext.getProperties()).isEmpty();
    }

    @Test
    void surfacesClassCastFailuresFromFactoriesThatReturnTheWrongType() {
        System.clearProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
        ClassLoader testClassLoader = ContextFinderTest.class.getClassLoader();

        assertThatThrownBy(() -> JAXBContext.newInstance(
                "org_apache_openejb.javaee_api.contextpath.badcast",
                testClassLoader,
                Collections.<String, Object>emptyMap()))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining(WrongContextType.class.getName());
    }

    public static final class MultiContextFactory {
        public static JAXBContext createContext(String contextPath, ClassLoader classLoader, Map<String, ?> properties) {
            return new RecordingJaxbContext("string-map", contextPath, classLoader, properties, null, null);
        }

        public static JAXBContext createContext(Class<?>[] classes, Map<String, ?> properties) {
            return new RecordingJaxbContext(
                    "classes-map",
                    null,
                    null,
                    properties,
                    classes,
                    Thread.currentThread().getContextClassLoader());
        }
    }

    public static final class TwoArgOnlyContextFactory {
        public static JAXBContext createContext(String contextPath, ClassLoader classLoader) {
            return new RecordingJaxbContext("string-two-arg", contextPath, classLoader, null, null, null);
        }
    }

    public static final class BadCastContextFactory {
        public static Object createContext(String contextPath, ClassLoader classLoader, Map<String, ?> properties) {
            return new WrongContextType();
        }
    }

    public static final class WrongContextType {
    }

    public static final class RecordingJaxbContext extends JAXBContext {
        private final String creationMode;
        private final String contextPath;
        private final ClassLoader classLoader;
        private final Map<String, ?> properties;
        private final Class<?>[] boundClasses;
        private final ClassLoader invocationThreadContextClassLoader;

        RecordingJaxbContext(
                String creationMode,
                String contextPath,
                ClassLoader classLoader,
                Map<String, ?> properties,
                Class<?>[] boundClasses,
                ClassLoader invocationThreadContextClassLoader) {
            this.creationMode = creationMode;
            this.contextPath = contextPath;
            this.classLoader = classLoader;
            this.properties = properties;
            this.boundClasses = boundClasses == null ? null : boundClasses.clone();
            this.invocationThreadContextClassLoader = invocationThreadContextClassLoader;
        }

        String getCreationMode() {
            return creationMode;
        }

        String getContextPath() {
            return contextPath;
        }

        ClassLoader getClassLoader() {
            return classLoader;
        }

        Map<String, ?> getProperties() {
            return properties;
        }

        Class<?>[] getBoundClasses() {
            return boundClasses == null ? null : boundClasses.clone();
        }

        ClassLoader getInvocationThreadContextClassLoader() {
            return invocationThreadContextClassLoader;
        }

        @Override
        public Unmarshaller createUnmarshaller() {
            return null;
        }

        @Override
        public Marshaller createMarshaller() {
            return null;
        }

        @Override
        public Validator createValidator() {
            return null;
        }
    }
}
