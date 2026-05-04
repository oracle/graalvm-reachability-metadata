/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

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

import java.util.ArrayList;
import java.util.Arrays;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CollectionPropertyCodecProviderInnerCollectionCodecTest {
    @Test
    void decodesPojoPropertyIntoConcreteParameterizedCollection() {
        final BsonDocument document = new BsonDocument("values", new BsonArray(Arrays.asList(
                new BsonString("alpha"), BsonNull.VALUE, new BsonString("bravo"))));

        final CollectionHolder decoded = decode(CollectionHolder.class, document);

        assertEquals(StringValues.class, decoded.getValues().getClass());
        assertEquals(Arrays.asList("alpha", null, "bravo"), decoded.getValues());
    }

    private static <T> T decode(final Class<T> type, final BsonDocument document) {
        final PojoCodecProvider pojoCodecProvider = PojoCodecProvider.builder().register(type).build();
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), pojoCodecProvider);
        final Codec<T> codec = registry.get(type);

        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static final class CollectionHolder {
        private StringValues<String> values;

        public CollectionHolder() {
        }

        public StringValues<String> getValues() {
            return values;
        }

        public void setValues(final StringValues<String> values) {
            this.values = values;
        }
    }

    public static final class StringValues<T> extends ArrayList<T> {
        private static final long serialVersionUID = 1L;

        public StringValues() {
        }
    }
}
