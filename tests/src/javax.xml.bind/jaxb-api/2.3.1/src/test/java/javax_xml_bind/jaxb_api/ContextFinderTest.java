/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collections;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax_xml_bind.jaxb_api.classproperties.PropertiesBoundType;
import javax_xml_bind.jaxb_api.noprovider.NoProviderBoundType;
import javax_xml_bind.jaxb_api.support.InterfaceContextFactory;
import javax_xml_bind.jaxb_api.support.StubJaxbContext;
import javax_xml_bind.jaxb_api.wrongtype.WrongTypeBoundType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ContextFinderTest {
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
    public void loadsContextPathFactoryFromJaxbPropertiesWithExplicitClassLoader() throws Exception {
        JAXBContext context = JAXBContext.newInstance(
                "javax_xml_bind.jaxb_api.objectfactorypath",
                getClass().getClassLoader(),
                Collections.singletonMap("path", "objectfactory"));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("three-argument-context-factory");
    }

    @Test
    public void usesSystemResourceLookupWhenContextFinderReceivesNullClassLoader() throws Exception {
        String resourceName = "javax_xml_bind/jaxb_api/objectfactorypath/jaxb.properties";

        URL resourceUrl = invokeContextFinderResourceLookup(resourceName);

        assertThat(resourceUrl).isEqualTo(ClassLoader.getSystemResource(resourceName));
        assertThat(resourceUrl).isNotNull();
    }

    @Test
    public void loadsDeprecatedServiceFactoryWithExplicitClassLoader() throws Exception {
        JAXBContext context = JAXBContext.newInstance(
                "javax_xml_bind.jaxb_api.noprovider",
                getClass().getClassLoader(),
                Collections.emptyMap());

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("deprecated-service-context-factory-string");
    }

    @Test
    public void loadsClassArrayFactoryFromPackageProperties() throws Exception {
        JAXBContext context = JAXBContext.newInstance(PropertiesBoundType.class);

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("classes-context-factory");
    }

    @Test
    public void instantiatesJaxbContextFactoryProvidersFromSystemProperty() throws Exception {
        System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY, InterfaceContextFactory.class.getName());

        JAXBContext context = JAXBContext.newInstance(NoProviderBoundType.class);

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("interface-context-factory-classes");
    }

    @Test
    public void throwsJaxbExceptionWhenFactoryReturnsWrongType() {
        assertThatThrownBy(() -> JAXBContext.newInstance(WrongTypeBoundType.class))
                .isInstanceOf(JAXBException.class);
    }

    private static URL invokeContextFinderResourceLookup(String resourceName) throws Exception {
        Class<?> contextFinderClass = Class.forName("javax.xml.bind.ContextFinder");
        Method getResourceUrl = contextFinderClass.getDeclaredMethod("getResourceUrl", ClassLoader.class, String.class);
        getResourceUrl.setAccessible(true);
        return (URL) getResourceUrl.invoke(null, new Object[]{null, resourceName});
    }
}
