/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import java.util.LinkedHashMap;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

public class MapPropertyCodecProviderInnerMapCodecTest {
    private static final CodecRegistry CODEC_REGISTRY = fromRegistries(
            fromProviders(new ValueCodecProvider()),
            fromProviders(PojoCodecProvider.builder().automatic(true).build()));

    @Test
    void decodesConcreteMapPropertyUsingItsNoArgumentConstructor() {
        MapHolder decoded = decode(documentWithLabels(), MapHolder.class);

        assertThat(decoded.getLabels()).isInstanceOf(OrderedLabels.class);
        assertThat(decoded.getLabels()).containsEntry("first", "alpha");
        assertThat(decoded.getLabels()).containsEntry("second", "beta");
    }

    private static BsonDocument documentWithLabels() {
        BsonDocument labels = new BsonDocument();
        labels.append("first", new BsonString("alpha"));
        labels.append("second", new BsonString("beta"));

        BsonDocument document = new BsonDocument();
        document.append("labels", labels);
        return document;
    }

    private static <T> T decode(BsonDocument document, Class<T> type) {
        Codec<T> codec = CODEC_REGISTRY.get(type);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static class MapHolder {
        private OrderedLabels<String, String> labels;

        public MapHolder() {
        }

        public OrderedLabels<String, String> getLabels() {
            return labels;
        }

        public void setLabels(OrderedLabels<String, String> labels) {
            this.labels = labels;
        }
    }

    public static class OrderedLabels<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;
    }
}
