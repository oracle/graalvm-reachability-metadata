/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_bind.jakarta_xml_bind_api;

import java.util.Map;

import jakarta.xml.bind.JAXBContext;
import jakarta_xml_bind.jakarta_xml_bind_api.support.StubJaxbContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleUtilTest {
    private static final String CONTEXT_PATH =
            "jakarta_xml_bind.jakarta_xml_bind_api.contextpath.properties"
                    + ":jakarta_xml_bind.jakarta_xml_bind_api.contextpath.indexed";

    @Test
    public void resolvesObjectFactoryAndJaxbIndexEntriesFromTheContextPath() throws Exception {
        JAXBContext context = JAXBContext.newInstance(CONTEXT_PATH, getClass().getClassLoader(), Map.of());

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("properties-context-path-factory");
    }
}
