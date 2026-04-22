/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax_xml_bind.jaxb_api.support.StubJaxbContext;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleUtilTest {
    @Test
    public void resolvesObjectFactoryClassesFromContextPath() throws Exception {
        TrackingClassLoader classLoader = new TrackingClassLoader(getClass().getClassLoader());

        JAXBContext context = JAXBContext.newInstance(
                "javax_xml_bind.jaxb_api.objectfactorypath",
                classLoader,
                Collections.emptyMap());

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("three-argument-context-factory");
        assertThat(classLoader.getLoadedClasses())
                .contains("javax_xml_bind.jaxb_api.objectfactorypath.ObjectFactory");
    }

    @Test
    public void resolvesIndexedClassesFromContextPath() throws Exception {
        TrackingClassLoader classLoader = new TrackingClassLoader(getClass().getClassLoader());

        JAXBContext context = JAXBContext.newInstance(
                "javax_xml_bind.jaxb_api.indexpath",
                classLoader,
                Collections.emptyMap());

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("three-argument-context-factory");
        assertThat(classLoader.getResourceRequests())
                .contains("javax_xml_bind/jaxb_api/indexpath/jaxb.index");
        assertThat(classLoader.getLoadedClasses())
                .contains(
                        "javax_xml_bind.jaxb_api.indexpath.ObjectFactory",
                        "javax_xml_bind.jaxb_api.indexpath.IndexedType");
    }

    private static final class TrackingClassLoader extends ClassLoader {
        private final List<String> loadedClasses = new ArrayList<>();
        private final List<String> resourceRequests = new ArrayList<>();

        private TrackingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedClasses.add(name);
            return super.loadClass(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            resourceRequests.add(name);
            return super.getResourceAsStream(name);
        }

        private List<String> getLoadedClasses() {
            return loadedClasses;
        }

        private List<String> getResourceRequests() {
            return resourceRequests;
        }
    }
}
