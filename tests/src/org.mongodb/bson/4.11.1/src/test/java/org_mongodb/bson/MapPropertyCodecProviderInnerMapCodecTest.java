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

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class MapPropertyCodecProviderInnerMapCodecTest {
    @Test
    void decodesConcreteMapPropertyUsingDeclaredConstructor() {
        final BsonDocument attributes = new BsonDocument()
                .append("first", new BsonString("alpha"))
                .append("missing", BsonNull.VALUE)
                .append("second", new BsonString("bravo"));
        final BsonDocument document = new BsonDocument("attributes", attributes);

        final Container decoded = decode(Container.class, document);

        assertThat(decoded.getAttributes()).isInstanceOf(StringMap.class);
        assertThat(decoded.getAttributes())
                .containsEntry("first", "alpha")
                .containsEntry("missing", null)
                .containsEntry("second", "bravo");
    }

    private static <T> T decode(final Class<T> type, final BsonDocument document) {
        final PojoCodecProvider pojoCodecProvider = PojoCodecProvider.builder().register(type).build();
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), pojoCodecProvider);
        final Codec<T> codec = registry.get(type);

        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static final class Container {
        private StringMap<String, String> attributes;

        public Container() {
        }

        public StringMap<String, String> getAttributes() {
            return attributes;
        }

        public void setAttributes(final StringMap<String, String> attributes) {
            this.attributes = attributes;
        }
    }

    public static final class StringMap<K, V> extends LinkedHashMap<K, V> {
        public StringMap() {
        }
    }
}
