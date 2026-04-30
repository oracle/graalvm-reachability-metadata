/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.python.core;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import net.razorvine.serpent.Serializer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializerTest {
    @Test
    public void serializesKnownJythonObjectsThroughPublicSerializerApi() {
        Serializer serializer = new Serializer();
        List<Object> values = Arrays.asList(
                new PyTuple("alpha", 7, true),
                new PyComplex(3.5, -2.25),
                new PyByteArray(new byte[] {'h', 'e', 'l', 'l', 'o' }),
                new PyMemoryView(new byte[] {'w', 'o', 'r', 'l', 'd' }));

        String body = serializeBody(serializer, values);

        assertThat(body)
                .startsWith("[('alpha',7,True),(3.5-2.25j),")
                .contains("'data':'aGVsbG8='")
                .contains("'data':'d29ybGQ='")
                .contains("'encoding':'base64'");
    }

    @Test
    public void serializesSerializablePropertiesThroughPublicGetters() throws Exception {
        Serializer serializer = new Serializer(false, true, true);
        URI uri = new URI("https://example.com:8443/docs?q=serpent#section");

        String body = serializeBody(serializer, uri);

        assertThat(body)
                .contains("'__class__':'java.net.URI'")
                .contains("'scheme':'https'")
                .contains("'host':'example.com'")
                .contains("'path':'/docs'")
                .contains("'query':'q=serpent'")
                .contains("'port':8443")
                .contains("'absolute':True")
                .contains("'opaque':False");
    }

    private static String serializeBody(Serializer serializer, Object value) {
        String serialized = new String(serializer.serialize(value), StandardCharsets.UTF_8);
        int firstLineEnd = serialized.indexOf('\n');
        return serialized.substring(firstLineEnd + 1);
    }
}
