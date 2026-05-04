/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MapPropertyCodecProviderInnerMapCodecTest {
    @Test
    void decodesPojoPropertyIntoConcreteParameterizedMap() {
        final BsonDocument document = new BsonDocument("values", new BsonDocument()
                .append("first", new BsonString("alpha"))
                .append("missing", BsonNull.VALUE)
                .append("second", new BsonString("bravo")));

        final MapHolder decoded = decode(MapHolder.class, document);

        assertEquals(StringMap.class, decoded.getValues().getClass());
        assertEquals(3, decoded.getValues().size());
        assertEquals("alpha", decoded.getValues().get("first"));
        assertNull(decoded.getValues().get("missing"));
        assertEquals("bravo", decoded.getValues().get("second"));
    }

    private static <T> T decode(final Class<T> type, final BsonDocument document) {
        final PojoCodecProvider pojoCodecProvider = PojoCodecProvider.builder().register(type).build();
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), pojoCodecProvider);
        final Codec<T> codec = registry.get(type);

        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static final class MapHolder {
        private StringMap<String, String> values;

        public MapHolder() {
        }

        public StringMap<String, String> getValues() {
            return values;
        }

        public void setValues(final StringMap<String, String> values) {
            this.values = values;
        }
    }

    public static final class StringMap<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;

        public StringMap() {
        }
    }
}
