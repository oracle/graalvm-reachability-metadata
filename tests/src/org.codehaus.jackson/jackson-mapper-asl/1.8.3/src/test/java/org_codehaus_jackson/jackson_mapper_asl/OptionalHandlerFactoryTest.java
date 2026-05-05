/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.datatype.XMLGregorianCalendar;

import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ext.OptionalHandlerFactory;
import org.junit.jupiter.api.Test;

public class OptionalHandlerFactoryTest {
    @Test
    public void instantiatesCoreXmlSerializerProviderForXmlTypes() {
        ObjectMapper mapper = new ObjectMapper();

        JsonSerializer<?> serializer = OptionalHandlerFactory.instance.findSerializer(
                mapper.getSerializationConfig(), mapper.constructType(XMLGregorianCalendar.class));

        assertThat(serializer).isNotNull();
        assertThat(serializer.handledType()).isEqualTo(XMLGregorianCalendar.class);
    }
}
