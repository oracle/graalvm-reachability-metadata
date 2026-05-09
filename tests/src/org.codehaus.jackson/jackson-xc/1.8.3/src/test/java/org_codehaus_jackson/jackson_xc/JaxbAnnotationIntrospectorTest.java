/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_xc;

import javax.xml.bind.annotation.XmlEnumValue;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JaxbAnnotationIntrospectorTest {
    @Test
    void serializesEnumUsingXmlEnumValue() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setAnnotationIntrospector(new JaxbAnnotationIntrospector());

        String json = mapper.writeValueAsString(Status.ENABLED);

        assertThat(json).isEqualTo("\"enabled-for-xml\"");
    }

    private enum Status {
        @XmlEnumValue("enabled-for-xml")
        ENABLED,
        DISABLED
    }
}
