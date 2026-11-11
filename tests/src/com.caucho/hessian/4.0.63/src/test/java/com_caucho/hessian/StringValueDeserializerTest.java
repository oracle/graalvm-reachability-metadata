/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.ExtSerializerFactory;
import com.caucho.hessian.io.SerializerFactory;
import com.caucho.hessian.io.StringValueDeserializer;
import com.caucho.hessian.io.StringValueSerializer;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class StringValueDeserializerTest {
    @Test
    void decodesStringConstructedValueWithRegisteredCodec() throws Exception {
        ExtSerializerFactory extension = new ExtSerializerFactory();
        extension.addSerializer(URI.class, new StringValueSerializer());
        extension.addDeserializer(URI.class, new StringValueDeserializer(URI.class));
        SerializerFactory factory = new SerializerFactory();
        factory.addFactory(extension);
        URI original = URI.create("urn:consistency:snapshot:17");
        assertEquals(original, SerializationSupport.roundTrip(original, factory, true));
    }
}
