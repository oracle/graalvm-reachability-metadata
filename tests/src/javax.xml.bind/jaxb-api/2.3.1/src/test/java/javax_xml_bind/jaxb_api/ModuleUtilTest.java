/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api;

import java.util.Collections;

import javax.xml.bind.JAXBContext;
import javax_xml_bind.jaxb_api.support.StubJaxbContext;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleUtilTest {
    @Test
    public void resolvesObjectFactoryClassesFromContextPath() throws Exception {
        JAXBContext context = JAXBContext.newInstance(
                "javax_xml_bind.jaxb_api.objectfactorypath",
                ClassLoader.getSystemClassLoader(),
                Collections.emptyMap());

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("three-argument-context-factory");
    }

    @Test
    public void resolvesIndexedClassesFromContextPath() throws Exception {
        JAXBContext context = JAXBContext.newInstance(
                "javax_xml_bind.jaxb_api.indexpath",
                ClassLoader.getSystemClassLoader(),
                Collections.emptyMap());

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("three-argument-context-factory");
    }

    @Test
    public void resolvesMixedContextPathPackagesWithSystemClassLoader() throws Exception {
        JAXBContext context = JAXBContext.newInstance(
                "javax_xml_bind.jaxb_api.objectfactorypath:javax_xml_bind.jaxb_api.indexpath",
                ClassLoader.getSystemClassLoader(),
                Collections.emptyMap());

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("three-argument-context-factory");
    }
}
