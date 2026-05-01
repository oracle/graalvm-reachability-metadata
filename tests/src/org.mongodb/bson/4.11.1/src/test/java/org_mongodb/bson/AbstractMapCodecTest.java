/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.MapCodecProvider;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.json.JsonReader;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class AbstractMapCodecTest {
    @Test
    void decodesCustomMapUsingItsNoArgumentConstructor() {
        final Codec<ConstructorBackedMap> codec = registry().get(ConstructorBackedMap.class);

        final ConstructorBackedMap map = codec.decode(
                new JsonReader("{\"name\": \"alpha\", \"count\": 7, \"empty\": null}"), DecoderContext.builder().build());

        assertThat(map).isInstanceOf(ConstructorBackedMap.class);
        assertThat(map).containsEntry("name", "alpha");
        assertThat(map).containsEntry("count", 7);
        assertThat(map).containsEntry("empty", null);
    }

    private static CodecRegistry registry() {
        return fromProviders(
                new ValueCodecProvider(),
                new BsonValueCodecProvider(),
                new MapCodecProvider());
    }

    public static final class ConstructorBackedMap extends LinkedHashMap<String, Object> {
        public ConstructorBackedMap() {
        }
    }
}
