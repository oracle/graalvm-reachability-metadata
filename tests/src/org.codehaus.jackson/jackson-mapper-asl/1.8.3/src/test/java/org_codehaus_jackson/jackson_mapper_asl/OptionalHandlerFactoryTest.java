/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import javax.xml.namespace.QName;

import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ext.OptionalHandlerFactory;
import org.codehaus.jackson.type.JavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OptionalHandlerFactoryTest {
    @Test
    void findsAndUsesCoreXmlSerializerLoadedByName() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        QName name = new QName("urn:jackson-test", "entry");
        JavaType type = mapper.constructType(QName.class);

        JsonSerializer<?> serializer = OptionalHandlerFactory.instance.findSerializer(
                mapper.getSerializationConfig(),
                type);

        assertThat(serializer).isNotNull();
        assertThat(mapper.writeValueAsString(name)).isEqualTo("\"{urn:jackson-test}entry\"");
    }

    @Test
    void findsAndUsesCoreXmlDeserializerLoadedByName() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.constructType(QName.class);

        JsonDeserializer<?> deserializer = OptionalHandlerFactory.instance.findDeserializer(
                type,
                mapper.getDeserializationConfig(),
                mapper.getDeserializerProvider());
        QName name = mapper.readValue("\"{urn:jackson-test}entry\"", QName.class);

        assertThat(deserializer).isNotNull();
        assertThat(name).isEqualTo(new QName("urn:jackson-test", "entry"));
    }
}
