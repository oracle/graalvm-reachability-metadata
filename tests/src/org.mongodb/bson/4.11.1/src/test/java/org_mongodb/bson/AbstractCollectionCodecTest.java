/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.Codec;
import org.bson.codecs.CollectionCodecProvider;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.json.JsonReader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class AbstractCollectionCodecTest {
    @Test
    void decodesCustomCollectionUsingItsNoArgumentConstructor() {
        final Codec<ConstructorBackedCollection> codec = registry().get(ConstructorBackedCollection.class);

        final ConstructorBackedCollection collection = codec.decode(
                new JsonReader("[\"first\", 42, null]"), DecoderContext.builder().build());

        assertThat(collection).isInstanceOf(ConstructorBackedCollection.class);
        assertThat(collection).containsExactly("first", 42, null);
    }

    private static CodecRegistry registry() {
        return fromProviders(
                new ValueCodecProvider(),
                new BsonValueCodecProvider(),
                new CollectionCodecProvider());
    }

    public static final class ConstructorBackedCollection extends ArrayList<Object> {
        public ConstructorBackedCollection() {
        }
    }
}
