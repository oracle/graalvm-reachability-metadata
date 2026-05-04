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
    void decodesConcreteMapUsingPublicNoArgumentConstructor() {
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), new MapCodecProvider());
        final Codec<CustomStringObjectMap> codec = registry.get(CustomStringObjectMap.class);
        final BsonDocument document = new BsonDocument()
                .append("first", new BsonString("alpha"))
                .append("missing", BsonNull.VALUE)
                .append("answer", new BsonInt32(42));

        final CustomStringObjectMap decoded = codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());

        assertThat(decoded).isExactlyInstanceOf(CustomStringObjectMap.class);
        assertThat(decoded).containsEntry("first", "alpha");
        assertThat(decoded).containsEntry("missing", null);
        assertThat(decoded).containsEntry("answer", 42);
    }

    public static final class CustomStringObjectMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }
}
