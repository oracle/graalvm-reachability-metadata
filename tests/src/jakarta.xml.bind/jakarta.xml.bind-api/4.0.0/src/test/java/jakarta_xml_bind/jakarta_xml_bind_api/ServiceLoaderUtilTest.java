/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_bind.jakarta_xml_bind_api;

import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.ServiceLoaderUtilInvoker;
import jakarta_xml_bind.jakarta_xml_bind_api.classproperties.PropertiesBoundType;
import jakarta_xml_bind.jakarta_xml_bind_api.support.FactoryBackedContextFactory;
import jakarta_xml_bind.jakarta_xml_bind_api.support.StubJaxbContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ServiceLoaderUtilTest {
    private static final String OSGI_CONTEXT_PATH =
            "jakarta_xml_bind.jakarta_xml_bind_api.contextpath.osgi";
    private static final String JAXB_CONTEXT_SERVICE_RESOURCE = "META-INF/services/jakarta.xml.bind.JAXBContext";

    @Test
    public void loadsProviderClassUsingContextClassLoader() throws Exception {
        JAXBContext context = withContextClassLoader(
                getClass().getClassLoader(),
                () -> JAXBContext.newInstance(PropertiesBoundType.class));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("properties-classes-factory");
    }

    @Test
    public void loadsProviderClassWithoutContextClassLoader() throws Exception {
        JAXBContext context = withContextClassLoader(null, () -> JAXBContext.newInstance(PropertiesBoundType.class));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("properties-classes-factory");
    }

    @Test
    public void instantiatesProviderClassByName() throws Exception {
        Object provider = withContextClassLoader(
                null,
                ServiceLoaderUtilInvoker::instantiateFactoryBackedContextFactory);

        assertThat(provider).isInstanceOf(FactoryBackedContextFactory.class);
    }

    @Test
    public void attemptsOsgiLookupBeforeFailingOverToTheMissingDefaultProvider() {
        ClassLoader classLoader = new ServiceResourceHidingClassLoader(getClass().getClassLoader());

        assertThatThrownBy(() -> JAXBContext.newInstance(OSGI_CONTEXT_PATH, classLoader, Map.of()))
                .isInstanceOf(JAXBException.class);
    }

    private static <T> T withContextClassLoader(
            ClassLoader classLoader,
            ThrowingSupplier<T> supplier) throws Exception {
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

    private static final class ServiceResourceHidingClassLoader extends ClassLoader {
        private ServiceResourceHidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            if (JAXB_CONTEXT_SERVICE_RESOURCE.equals(name)) {
                return null;
            }
            return super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (JAXB_CONTEXT_SERVICE_RESOURCE.equals(name)) {
                return null;
            }
            return super.getResourceAsStream(name);
        }
    }
}
