/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonString;
import org.bson.codecs.BsonValueCodecProvider;
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
    void decodesConcreteMapPropertyUsingItsNoArgumentConstructor() {
        final BsonDocument settings = new BsonDocument()
                .append("primary", new BsonString("red"))
                .append("secondary", new BsonString("blue"));
        final BsonDocument document = new BsonDocument("settings", settings);

        final MapPropertyPojo pojo = decode(document, MapPropertyPojo.class);

        assertThat(pojo.getSettings()).isInstanceOf(ConstructorBackedMap.class);
        assertThat(pojo.getSettings()).containsEntry("primary", "red");
        assertThat(pojo.getSettings()).containsEntry("secondary", "blue");
    }

    private static <T> T decode(final BsonDocument document, final Class<T> pojoClass) {
        final CodecRegistry registry = fromProviders(
                new ValueCodecProvider(),
                new BsonValueCodecProvider(),
                PojoCodecProvider.builder().register(pojoClass).build());
        final Codec<T> codec = registry.get(pojoClass);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static final class MapPropertyPojo {
        private ConstructorBackedMap<String, String> settings;

        public MapPropertyPojo() {
        }

        public ConstructorBackedMap<String, String> getSettings() {
            return settings;
        }

        public void setSettings(final ConstructorBackedMap<String, String> settings) {
            this.settings = settings;
        }
    }

    public static final class ConstructorBackedMap<K, V> extends LinkedHashMap<K, V> {
        public ConstructorBackedMap() {
        }
    }
}
