/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api;

import java.util.Collections;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax_xml_bind.jaxb_api.classproperties.PropertiesBoundType;
import javax_xml_bind.jaxb_api.support.StubJaxbContext;
import javax_xml_bind.jaxb_api.wrongtype.WrongTypeBoundType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ContextFinderTest {
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
    public void loadsContextPathFactoryFromJaxbPropertiesWithNullClassLoader() throws Exception {
        JAXBContext context = JAXBContext.newInstance(
                "javax_xml_bind.jaxb_api.objectfactorypath",
                null,
                Collections.singletonMap("path", "objectfactory"));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("three-argument-context-factory");
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
    public void throwsJaxbExceptionWhenFactoryReturnsWrongType() {
        assertThatThrownBy(() -> JAXBContext.newInstance(WrongTypeBoundType.class))
                .isInstanceOf(JAXBException.class);
    }
}
