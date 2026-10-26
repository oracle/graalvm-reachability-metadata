/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

import org.apache.pulsar.shade.javax.xml.bind.JAXBContext;
import org.apache.pulsar.shade.javax.xml.bind.JAXBContextFactory;
import org.apache.pulsar.shade.javax.xml.bind.JAXBException;
import org.apache.pulsar.shade.javax.xml.bind.Marshaller;
import org.apache.pulsar.shade.javax.xml.bind.Unmarshaller;
import org.apache.pulsar.shade.javax.xml.bind.Validator;
import org.junit.jupiter.api.Test;

public class ContextFinderTest {
    @Test
    void discoversClassArrayFactoryFromSystemProperty() throws Exception {
        String previous = System.getProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
        System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY, TestContextFactory.class.getName());
        try {
            JAXBContext context = JAXBContext.newInstance(new Class<?>[] {BoundValue.class}, Collections.emptyMap());

            assertThat(context).isInstanceOf(StubContext.class);
            assertThat(((StubContext) context).source).isEqualTo("classes");
        } finally {
            restoreFactory(previous);
        }
    }

    @Test
    void invokesContextPathFactoryWithProperties() throws Exception {
        String previous = System.getProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
        System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY, TestContextFactory.class.getName());
        try {
            JAXBContext context = JAXBContext.newInstance(
                    "org_apache_pulsar.pulsar_client_all",
                    getClass().getClassLoader(),
                    Map.of("mode", "client"));

            assertThat(context).isInstanceOf(StubContext.class);
            assertThat(((StubContext) context).source).isEqualTo("path");
        } finally {
            restoreFactory(previous);
        }
    }

    @Test
    void invokesLegacyContextPathFactoryContract() throws Exception {
        String previous = System.getProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
        System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY, LegacyContextFactory.class.getName());
        try {
            JAXBContext context = JAXBContext.newInstance(
                    "org_apache_pulsar.pulsar_client_all", getClass().getClassLoader(), Collections.emptyMap());

            assertThat(context).isInstanceOf(StubContext.class);
            assertThat(((StubContext) context).source).isEqualTo("legacy-path");
        } finally {
            restoreFactory(previous);
        }
    }

    private static void restoreFactory(String previous) {
        if (previous == null) {
            System.clearProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
        } else {
            System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY, previous);
        }
    }

    public static class BoundValue {
    }

    public static class TestContextFactory implements JAXBContextFactory {
        public TestContextFactory() {
        }

        @Override
        public JAXBContext createContext(Class<?>[] classes, Map<String, ?> properties) {
            return new StubContext("classes");
        }

        @Override
        public JAXBContext createContext(String contextPath, ClassLoader classLoader, Map<String, ?> properties) {
            return new StubContext("path");
        }
    }

    public static class LegacyContextFactory {
        public static JAXBContext createContext(String contextPath, ClassLoader classLoader) {
            return new StubContext("legacy-path");
        }
    }

    public static class StubContext extends JAXBContext {
        private final String source;

        StubContext(String source) {
            this.source = source;
        }

        @Override
        public Unmarshaller createUnmarshaller() throws JAXBException {
            throw new JAXBException("not used");
        }

        @Override
        public Marshaller createMarshaller() throws JAXBException {
            throw new JAXBException("not used");
        }

        @Override
        public Validator createValidator() throws JAXBException {
            throw new JAXBException("not used");
        }
    }
}
