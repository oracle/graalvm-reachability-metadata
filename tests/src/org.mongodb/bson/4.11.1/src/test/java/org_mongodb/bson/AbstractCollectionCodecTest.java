/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

import java.util.LinkedList;

import org.bson.codecs.Codec;
import org.bson.codecs.CollectionCodecProvider;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.json.JsonReader;
import org.junit.jupiter.api.Test;

public class AbstractCollectionCodecTest {
    private static final CodecRegistry CODEC_REGISTRY = fromProviders(
            new ValueCodecProvider(),
            new CollectionCodecProvider());

    @Test
    void decodesConcreteCollectionUsingItsNoArgumentConstructor() {
        Codec<LinkedList> codec = CODEC_REGISTRY.get(LinkedList.class);

        LinkedList<?> decoded = codec.decode(
                new JsonReader("[\"first\", null, \"second\"]"),
                DecoderContext.builder().build());

        assertThat(decoded).isInstanceOf(LinkedList.class);
        assertThat(decoded.toArray()).containsExactly("first", null, "second");
    }
}
