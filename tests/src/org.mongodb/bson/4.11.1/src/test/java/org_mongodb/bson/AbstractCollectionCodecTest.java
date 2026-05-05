/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

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
    void decodesConcreteCollectionUsingItsNoArgumentConstructor() {
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), new CollectionCodecProvider());
        final Codec<CustomObjectList> codec = registry.get(CustomObjectList.class);

        final CustomObjectList decoded = codec.decode(
                new JsonReader("[\"alpha\", null, 42]"), DecoderContext.builder().build());

        assertThat(decoded).isInstanceOf(CustomObjectList.class);
        assertThat(decoded).containsExactly("alpha", null, 42);
    }

    public static final class CustomObjectList extends ArrayList<Object> {
        public CustomObjectList() {
        }
    }
}
