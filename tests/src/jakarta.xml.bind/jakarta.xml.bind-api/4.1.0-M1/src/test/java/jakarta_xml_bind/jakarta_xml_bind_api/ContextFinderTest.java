/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_bind.jakarta_xml_bind_api;

import java.util.Map;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta_xml_bind.jakarta_xml_bind_api.classproperties.PropertiesBoundType;
import jakarta_xml_bind.jakarta_xml_bind_api.factorybacked.FactoryBackedBoundType;
import jakarta_xml_bind.jakarta_xml_bind_api.servicebound.ServiceBoundType;
import jakarta_xml_bind.jakarta_xml_bind_api.support.StubJaxbContext;
import jakarta_xml_bind.jakarta_xml_bind_api.wrongtype.WrongTypeBoundType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ContextFinderTest {
    private static final String PROPERTIES_CONTEXT_PATH =
            "jakarta_xml_bind.jakarta_xml_bind_api.contextpath.properties";
    private static final String SERVICE_CONTEXT_PATH =
            "jakarta_xml_bind.jakarta_xml_bind_api.contextpath.service";
    private static final String PROPERTIES_CONTEXT_FACTORY = "DefaultPropertiesContextFactory";
    private static final String LEGACY_CONTEXT_FACTORY = "DefaultLegacyContextFactory";
    private static final String FACTORY_BACKED_CONTEXT_FACTORY = "DefaultFactoryBackedContextFactory";
    private static final String WRONG_TYPE_CONTEXT_FACTORY = "DefaultWrongTypeContextFactory";

    @Test
    public void loadsThreeArgumentFactoryFromPropertiesMapForContextPath() throws Exception {
        JAXBContext context = JAXBContext.newInstance(
                PROPERTIES_CONTEXT_PATH,
                getClass().getClassLoader(),
                Map.of(
                        JAXBContext.JAXB_CONTEXT_FACTORY,
                        PROPERTIES_CONTEXT_FACTORY,
                        "trigger",
                        "jaxb-properties"));

        assertContextSource(context, "properties-context-path-factory");
    }

    @Test
    public void loadsFactoryFromPropertiesMapForBoundClasses() throws Exception {
        JAXBContext context = JAXBContext.newInstance(
                new Class<?>[] {PropertiesBoundType.class},
                Map.of(JAXBContext.JAXB_CONTEXT_FACTORY, PROPERTIES_CONTEXT_FACTORY));

        assertContextSource(context, "properties-classes-factory");
    }

    @Test
    public void loadsServiceProviderForContextPath() throws Exception {
        JAXBContext context = JAXBContext.newInstance(
                SERVICE_CONTEXT_PATH,
                getClass().getClassLoader(),
                Map.of());

        assertContextSource(context, "service-context-factory-string");
    }

    @Test
    public void loadsLegacyTwoArgumentFactoryFromPropertiesMapForContextPath() throws Exception {
        JAXBContext context = JAXBContext.newInstance(
                PROPERTIES_CONTEXT_PATH,
                getClass().getClassLoader(),
                Map.of(JAXBContext.JAXB_CONTEXT_FACTORY, LEGACY_CONTEXT_FACTORY));

        assertContextSource(context, "legacy-context-path-factory");
    }

    @Test
    public void instantiatesJaxbContextFactoryImplementations() throws Exception {
        JAXBContext context = JAXBContext.newInstance(
                new Class<?>[] {FactoryBackedBoundType.class},
                Map.of(JAXBContext.JAXB_CONTEXT_FACTORY, FACTORY_BACKED_CONTEXT_FACTORY));

        assertContextSource(context, "factory-backed-classes-factory");
    }

    @Test
    public void loadsServiceProviderForBoundClassesWithoutContextClassLoader() throws Exception {
        JAXBContext context = withContextClassLoader(null, () -> JAXBContext.newInstance(ServiceBoundType.class));

        assertContextSource(context, "service-context-factory-classes");
    }

    @Test
    public void throwsHelpfulExceptionWhenProviderReturnsWrongType() {
        assertThatThrownBy(() -> JAXBContext.newInstance(
                new Class<?>[] {WrongTypeBoundType.class},
                Map.of(JAXBContext.JAXB_CONTEXT_FACTORY, WRONG_TYPE_CONTEXT_FACTORY)))
                .isInstanceOf(JAXBException.class)
                .hasMessageContaining("ClassCastException");
    }

    private static void assertContextSource(JAXBContext context, String expectedSource) {
        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo(expectedSource);
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
}
