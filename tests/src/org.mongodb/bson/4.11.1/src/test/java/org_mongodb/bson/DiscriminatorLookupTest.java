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
import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class DiscriminatorLookupTest {
    @Test
    void decodesPojoUsingFullyQualifiedDiscriminatorClassName() {
        final BsonDocument document = new BsonDocument()
                .append("_t", new BsonString(DiscriminatedChild.class.getName()))
                .append("name", new BsonString("child-value"));

        final DiscriminatedParent decoded = decode(document, DiscriminatedParent.class);

        assertThat(decoded).isInstanceOf(DiscriminatedChild.class);
        assertThat(((DiscriminatedChild) decoded).getName()).isEqualTo("child-value");
    }

    private static <T> T decode(final BsonDocument document, final Class<T> pojoClass) {
        final Codec<T> codec = registry().get(pojoClass);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    private static CodecRegistry registry() {
        final ClassModel<DiscriminatedParent> parentModel = ClassModel.builder(DiscriminatedParent.class)
                .enableDiscriminator(true)
                .discriminatorKey("_t")
                .discriminator(DiscriminatedParent.class.getName())
                .build();
        return fromProviders(
                new ValueCodecProvider(),
                new BsonValueCodecProvider(),
                PojoCodecProvider.builder()
                        .automatic(true)
                        .register(parentModel)
                        .build());
    }

    public static class DiscriminatedParent {
        public DiscriminatedParent() {
        }
    }

    public static final class DiscriminatedChild extends DiscriminatedParent {
        private String name;

        public DiscriminatedChild() {
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
