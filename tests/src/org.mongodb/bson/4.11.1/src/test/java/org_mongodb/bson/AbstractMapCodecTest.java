/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

import java.util.LinkedHashMap;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.MapCodecProvider;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.jupiter.api.Test;

public class AbstractMapCodecTest {
    private static final CodecRegistry CODEC_REGISTRY = fromProviders(
            new ValueCodecProvider(),
            new MapCodecProvider());

    @Test
    void decodesConcreteParameterizedMapUsingItsNoArgumentConstructor() {
        Codec<StringMap> codec = CODEC_REGISTRY.get(StringMap.class, asList(String.class, String.class));

        StringMap decoded = codec.decode(new BsonDocumentReader(document()), DecoderContext.builder().build());

        assertThat(decoded).isInstanceOf(StringMap.class);
        assertThat(decoded).containsEntry("first", "alpha");
        assertThat(decoded).containsEntry("second", "beta");
    }

    private static BsonDocument document() {
        BsonDocument document = new BsonDocument();
        document.append("first", new BsonString("alpha"));
        document.append("second", new BsonString("beta"));
        return document;
    }

    public static class StringMap extends LinkedHashMap<String, String> {
        private static final long serialVersionUID = 1L;
    }
}
