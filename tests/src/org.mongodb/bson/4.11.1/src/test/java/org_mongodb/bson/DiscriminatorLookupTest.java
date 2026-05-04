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
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class DiscriminatorLookupTest {
    @Test
    void decodesDiscriminatorClassNameThatWasNotPreRegistered() {
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), PojoCodecProvider.builder()
                .automatic(true)
                .build());
        final Codec<Animal> animalCodec = registry.get(Animal.class);
        final BsonDocument document = new BsonDocument()
                .append("_t", new BsonString(Dog.class.getName()))
                .append("name", new BsonString("Scout"));

        final Animal decoded = animalCodec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());

        assertThat(decoded).isInstanceOf(Dog.class);
        assertThat(decoded.getName()).isEqualTo("Scout");
    }

    @BsonDiscriminator
    public static class Animal {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }

    public static final class Dog extends Animal {
    }
}
