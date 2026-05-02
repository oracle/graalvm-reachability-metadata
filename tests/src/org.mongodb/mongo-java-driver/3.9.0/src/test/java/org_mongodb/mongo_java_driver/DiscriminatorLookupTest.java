/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import com.mongodb.MongoClient;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class DiscriminatorLookupTest {
    @Test
    void resolvesPojoSubtypeFromDiscriminatorClassName() {
        final Codec<BaseDiscriminatedPojo> codec = codecFor(BaseDiscriminatedPojo.class);
        final BsonDocument document = new BsonDocument()
                .append("_t", new BsonString(ResolvedDiscriminatedPojo.class.getName()))
                .append("name", new BsonString("resolved-by-discriminator"))
                .append("rank", new BsonInt32(7));

        final BaseDiscriminatedPojo decoded = codec.decode(
                new BsonDocumentReader(document), DecoderContext.builder().build());

        assertThat(decoded).isInstanceOf(ResolvedDiscriminatedPojo.class);
        final ResolvedDiscriminatedPojo resolved = (ResolvedDiscriminatedPojo) decoded;
        assertThat(resolved.getName()).isEqualTo("resolved-by-discriminator");
        assertThat(resolved.getRank()).isEqualTo(7);
    }

    private static <T> Codec<T> codecFor(final Class<T> pojoClass) {
        final ClassModel<T> classModel = ClassModel.builder(pojoClass)
                .enableDiscriminator(true)
                .discriminatorKey("_t")
                .discriminator(pojoClass.getName())
                .build();
        final CodecRegistry registry = fromRegistries(
                MongoClient.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder()
                        .automatic(true)
                        .register(classModel)
                        .build()));
        return registry.get(pojoClass);
    }

    public static class BaseDiscriminatedPojo {
        private String name;

        public BaseDiscriminatedPojo() {
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }

    public static class ResolvedDiscriminatedPojo extends BaseDiscriminatedPojo {
        private int rank;

        public ResolvedDiscriminatedPojo() {
        }

        public int getRank() {
            return rank;
        }

        public void setRank(final int rank) {
            this.rank = rank;
        }
    }
}
