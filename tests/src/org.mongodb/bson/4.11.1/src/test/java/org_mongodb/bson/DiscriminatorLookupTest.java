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

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.junit.jupiter.api.Test;

public class DiscriminatorLookupTest {
    private static final CodecRegistry CODEC_REGISTRY = fromRegistries(
            fromProviders(new ValueCodecProvider()),
            fromProviders(PojoCodecProvider.builder().automatic(true).build()));

    @Test
    void decodesPojoUsingFullyQualifiedDiscriminatorClassName() {
        BsonDocument document = new BsonDocument();
        document.append("_t", new BsonString(DiscriminatorDog.class.getName()));
        document.append("name", new BsonString("Barkley"));
        document.append("favoriteTrick", new BsonString("roll over"));

        DiscriminatorAnimal decoded = decode(document, DiscriminatorAnimal.class);

        assertThat(decoded).isInstanceOf(DiscriminatorDog.class);
        DiscriminatorDog dog = (DiscriminatorDog) decoded;
        assertThat(dog.getName()).isEqualTo("Barkley");
        assertThat(dog.getFavoriteTrick()).isEqualTo("roll over");
    }

    private static <T> T decode(BsonDocument document, Class<T> type) {
        Codec<T> codec = CODEC_REGISTRY.get(type);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    @BsonDiscriminator
    public static class DiscriminatorAnimal {
        private String name;

        public DiscriminatorAnimal() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class DiscriminatorDog extends DiscriminatorAnimal {
        private String favoriteTrick;

        public DiscriminatorDog() {
        }

        public String getFavoriteTrick() {
            return favoriteTrick;
        }

        public void setFavoriteTrick(String favoriteTrick) {
            this.favoriteTrick = favoriteTrick;
        }
    }
}
