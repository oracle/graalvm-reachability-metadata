/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.MapCodecProvider;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class AbstractMapCodecTest {
    @Test
    void decodesConcreteMapUsingItsNoArgumentConstructor() {
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), new MapCodecProvider());
        final Codec<CustomObjectMap> codec = registry.get(CustomObjectMap.class);
        final BsonDocument document = new BsonDocument()
                .append("name", new BsonString("alpha"))
                .append("count", new BsonInt32(7))
                .append("missing", BsonNull.VALUE);

        final CustomObjectMap decoded = codec.decode(
                new BsonDocumentReader(document), DecoderContext.builder().build());

        assertThat(decoded).isInstanceOf(CustomObjectMap.class);
        assertThat(decoded)
                .containsEntry("name", "alpha")
                .containsEntry("count", 7)
                .containsEntry("missing", null);
    }

    public static final class CustomObjectMap extends LinkedHashMap<String, Object> {
        public CustomObjectMap() {
        }
    }
}
