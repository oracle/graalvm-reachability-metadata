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
import org.bson.codecs.CollectionCodecProvider;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class AbstractCollectionCodecTest {
    @Test
    void decodesConcreteCollectionUsingPublicNoArgumentConstructor() {
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), new CollectionCodecProvider());
        final Codec<CustomStringValues> codec = registry.get(CustomStringValues.class);
        final BsonDocument document = new BsonDocument("values", new BsonArray(Arrays.asList(
                new BsonString("alpha"), BsonNull.VALUE, new BsonString("bravo"))));
        final BsonDocumentReader reader = new BsonDocumentReader(document);

        reader.readStartDocument();
        reader.readName("values");
        final CustomStringValues decoded = codec.decode(reader, DecoderContext.builder().build());
        reader.readEndDocument();

        assertThat(decoded).isExactlyInstanceOf(CustomStringValues.class);
        assertThat(decoded).containsExactly("alpha", null, "bravo");
    }

    public static final class CustomStringValues extends ArrayList<Object> {
        private static final long serialVersionUID = 1L;
    }
}
