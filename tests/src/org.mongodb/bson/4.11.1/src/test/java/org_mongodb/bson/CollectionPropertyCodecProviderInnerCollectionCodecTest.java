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

import java.util.ArrayList;

import org.bson.BsonArray;
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

public class CollectionPropertyCodecProviderInnerCollectionCodecTest {
    private static final CodecRegistry CODEC_REGISTRY = fromRegistries(
            fromProviders(new ValueCodecProvider()),
            fromProviders(PojoCodecProvider.builder().automatic(true).build()));

    @Test
    void decodesConcreteCollectionPropertyUsingItsNoArgumentConstructor() {
        CollectionHolder decoded = decode(documentWithValues(), CollectionHolder.class);

        assertThat(decoded.getValues()).isInstanceOf(OrderedValues.class);
        assertThat(decoded.getValues()).containsExactly("alpha", null, "omega");
    }

    private static BsonDocument documentWithValues() {
        BsonArray values = new BsonArray();
        values.add(new BsonString("alpha"));
        values.add(BsonNull.VALUE);
        values.add(new BsonString("omega"));

        BsonDocument document = new BsonDocument();
        document.append("values", values);
        return document;
    }

    private static <T> T decode(BsonDocument document, Class<T> type) {
        Codec<T> codec = CODEC_REGISTRY.get(type);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static class CollectionHolder {
        private OrderedValues<String> values;

        public CollectionHolder() {
        }

        public OrderedValues<String> getValues() {
            return values;
        }

        public void setValues(OrderedValues<String> values) {
            this.values = values;
        }
    }

    public static class OrderedValues<T> extends ArrayList<T> {
        private static final long serialVersionUID = 1L;
    }
}
